#!groovy

import groovy.json.JsonOutput

//library identifier: "build-check@develop",
//        retriever: modernSCM([$class: 'GitSCMSource',
//                              remote: "https://pagure.io/fedora-ci-generic-checks.git",
//                              traits: [[$class: 'jenkins.plugins.git.traits.BranchDiscoveryTrait'],
//                                       [$class: 'RefSpecsSCMSourceTrait',
//                                        templates: [[value: '+refs/heads/*:refs/remotes/@{remote}/*'],
//                                                    [value: '+refs/pull/*:refs/remotes/origin/pr/*']]]]])

// load all parts of buildCheckUtils, global library defined in jenkins config
@Library('buildCheckUtils') _

/**
 * Function to execute script in container
 * Container must have been defined in a podTemplate
 *
 * @param stageName Name of the stage
 * @param containerName Name of the container for script execution
 * @param script Complete path to the script to execute
 * @param vars Optional list of key=values to add to env
 * @return
 */
def executeInContainer(String stageName,
                       String containerName,
                       String script,
                       ArrayList<String> vars=null) {
    //
    // Kubernetes plugin does not let containers inherit
    // env vars from host. We force them in.
    //
    def containerEnv = env.getEnvironment().collect { key, value -> return "${key}=${value}" }
    if (vars){
        vars.each {x->
            containerEnv.add(x)
        }
    }

    sh "mkdir -p ${stageName}"
    try {
        withEnv(containerEnv) {
            container(containerName) {
                sh script
            }
        }
    } catch (err) {
        throw err
    } finally {
        sh """
        if [ -d "logs" ]; then
            mv -vf logs ${stageName}/logs || true
        else
            echo "No logs for executeInContainer(). Ignoring this." >&2
        fi
        """
    }
}

/**
 * Library to set message fields to be published
 * @param messageType: ${MAIN_TOPIC}.ci.pipeline.allpackages.<defined-in-README>
 * @param artifact ${MAIN_TOPIC}.ci.pipeline.allpackages-${artifact}.<defined-in-README>
 * @return
 */
def setMessageFields(String messageType, String artifact) {
    topic = "${MAIN_TOPIC}.ci.${messageType}"
    print("Topic is " + topic)

    // Create a HashMap of default message content keys and values
    // These properties should be applicable to ALL message types.
    // If something is applicable to only some subset of messages,
    // add it below per the existing examples.

    taskid = env.fed_task_id ?: env.fed_id

    def messageContent = [
            branch           : env.fed_branch,
            build_id         : env.BUILD_ID,
            build_url        : env.JENKINS_URL + '/'+ env.JOB_NAME + '/' + env.BUILD_NUMBER,
            namespace        : env.fed_namespace,
            nvr              : env.nvr,
            original_spec_nvr: env.original_spec_nvr,
            ci_topic         : topic,
            ref              : env.basearch,
            scratch          : env.isScratch ? env.isScratch.toBoolean() : "",
            repo             : env.fed_repo,
            rev              : (artifact == 'build') ? "kojitask-" + taskid : env.fed_rev,
            status           : currentBuild.currentResult,
            test_guidance    : "''",
            comment_id       : env.fed_lastcid,
            username         : env.fed_owner,
    ]

    if (artifact == 'pr') {
        messageContent.commit_hash = env.fed_last_commit_hash
    }

    // Add image type to appropriate message types
    if (messageType in ['image.queued', 'image.running', 'image.complete', 'image.test.smoke.queued', 'image.test.smoke.running', 'image.test.smoke.complete'
    ]) {
        messageContent.type = messageType == 'image.running' ? "''" : 'qcow2'
    }

    // Create a string to hold the data from the messageContent hash map
    String messageContentString = JsonOutput.toJson(messageContent)

    def messagePropertiesString = ''

    return [ 'topic': topic, 'properties': messagePropertiesString, 'content': messageContentString ]
}

/**
 * Library to send message
 * @param msgProps - Message Properties - empty string for fedmsg
 * @param msgContent - Message content in map form
 * @return
 */
def sendMessage(String msgTopic, String msgProps, String msgContent) {

    retry(10) {
        try {
            // 1 minute should be more than enough time to send the topic msg
            timeout(1) {
                try {
                    // Send message and return SendResult
                    sendResult = sendCIMessage messageContent: msgContent,
                            messageProperties: msgProps,
                            messageType: 'Custom',
                            overrides: [topic: msgTopic],
                            failOnError: true,
                            providerName: "${MSG_PROVIDER}"
                    return sendResult
                } catch(e) {
                    throw e
                }
            }
        } catch(e) {
            echo "FAIL: Could not send message to ${MSG_PROVIDER}"
            echo e.getMessage()
            sleep 30
            error e.getMessage()
        }
    }
}

    // Execution ID for this run of the pipeline
    def executionID = UUID.randomUUID().toString()
    def podName = 'fedora-package-check-' + executionID

    podTemplate(name: podName,
                label: podName,
                cloud: 'openshift',
                serviceAccount: OPENSHIFT_SERVICE_ACCOUNT,
                idleMinutes: 0,
                namespace: OPENSHIFT_NAMESPACE,
                // this is a temporary thing while getting all of this to work
                //podRetention: always(),

            containers: [
                    // adds the custom container
                    containerTemplate(name: 'package-checks',
                            alwaysPullImage: true,
                            image: DOCKER_REPO_URL + '/' + OPENSHIFT_NAMESPACE + '/fedora-generic-check-worker:latest',
                            ttyEnabled: true,
                            command: 'cat',
                            // Request - minimum required, Limit - maximum possible (hard quota)
                            // https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/#meaning-of-cpu
                            // https://blog.openshift.com/managing-compute-resources-openshiftkubernetes/
                            resourceRequestCpu: '500m',
                            resourceLimitCpu: '1',
                            resourceRequestMemory: '2Gi',
                            resourceLimitMemory: '4Gi',
                            privileged: false,
                            workingDir: '/home/jenkins')

            ],
            volumes: [emptyDirVolume(memory: false, mountPath: '/sys/class/net')])
    {
node(podName) {

    currentStage = "basic-information"
    stage(currentStage) {
        print env.CI_MESSAGE
    }

    currentStage = "run-rpminspect"
    stage(currentStage) {

        env.MSG_PROVIDER = "fedora-fedmsg"
        env.MAIN_TOPIC = env.MAIN_TOPIC ?: 'org.centos.stg'
        def json_message = readJSON text: env.CI_MESSAGE
        //env.TARGET_ENVR = "${json_message['name']}-${json_message['version']}-${json_message['release']}"
        // short circuiting failure for now for testing of error handling
        env.TARGET_ENVR = "blah-foo.fc99"

        // Set our message topic, properties, and content
        messageFields = setMessageFields("koji-build.test.queued", env.TARGET_ENVR)

        // Send message org.centos.prod.ci.pipeline.allpackages.package.test.functional.queued on fedmsg
        sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

        // Set stage specific vars
        //packagepipelineUtils.setStageEnvVars(currentStage)

        // Set our message topic, properties, and content
        messageFields = setMessageFields("koji-build.test.running", env.TARGET_ENVR)
        sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

        // Run functional tests
        try {
            executeInContainer(currentStage, "package-checks", "/tmp/run-rpminspect.sh")
        } catch (e) {
            if (buildCheckUtils.fileExists("${WORKSPACE}/${currentStage}/logs/test.log")) {
                currentBuild.result = 'UNSTABLE'
            } else {
                currentBuild.result = 'UNSTABLE'
                //throw e
            }
        }

        // Set our message topic, properties, and content
        messageFields = setMessageFields("koji-build.test.complete", env.TARGET_ENVR)

        // Send message org.centos.prod.ci.pipeline.allpackages.package.test.functional.complete on fedmsg
        sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

    }

    stage("archive output"){

        archiveArtifacts artifacts: 'run-rpminspect/logs/rpminspect.json'
        archiveArtifacts artifacts: 'run-rpminspect/logs/results.yaml'

    }

}

}
