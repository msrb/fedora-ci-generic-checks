#!groovy

import groovy.json.JsonOutput


def job = job('RPMinspect Basic') {

//library identifier: "build-check@develop",
//        retriever: modernSCM([$class: 'GitSCMSource',
//                              remote: "https://pagure.io/fedora-ci-generic-checks.git",
//                              traits: [[$class: 'jenkins.plugins.git.traits.BranchDiscoveryTrait'],
//                                       [$class: 'RefSpecsSCMSourceTrait',
//                                        templates: [[value: '+refs/heads/*:refs/remotes/@{remote}/*'],
//                                                    [value: '+refs/pull/*:refs/remotes/origin/pr/*']]]]])

// load all parts of buildCheckUtils, global library defined in jenkins config
@Library('buildCheckUtils') _



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

        // attempting to set MAIN_TOPIC in library
        buildCheckUtils.setupEnvVars(env.MAIN_TOPIC)

        def json_message = readJSON text: env.CI_MESSAGE
        //env.TARGET_ENVR = "${json_message['name']}-${json_message['version']}-${json_message['release']}"
        // short circuiting failure for now for testing of error handling
        env.TARGET_ENVR = "blah-foo.fc99"

        // Set our message topic, properties, and content
        messageFields = buildCheckUtils.setMessageFields("koji-build.test.queued", env.TARGET_ENVR)

        // Send message org.centos.prod.ci.pipeline.allpackages.package.test.functional.queued on fedmsg
        buildCheckUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

        // Set stage specific vars
        //packagepipelineUtils.setStageEnvVars(currentStage)

        // Set our message topic, properties, and content
        messageFields = buildCheckUtils.setMessageFields("koji-build.test.running", env.TARGET_ENVR)
        buildCheckUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

        // Run functional tests
        try {
            executeInContainer(currentStage, "package-checks", "/tmp/run-rpminspect.sh")
        } catch (e) {
            if (buildCheckUtils.fileExists("${WORKSPACE}/${currentStage}/logs/test.log")) {
                currentBuild.result = 'UNSTABLE'
            } else {
                throw e
            }
        }

        // Set our message topic, properties, and content
        messageFields = buildCheckUtils.setMessageFields("koji-build.test.complete", env.TARGET_ENVR)

        // Send message org.centos.prod.ci.pipeline.allpackages.package.test.functional.complete on fedmsg
        buildCheckUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

    }

    stage("archive output"){

        archiveArtifacts artifacts: 'run-rpminspect/logs/rpminspect.json'
        archiveArtifacts artifacts: 'run-rpminspect/logs/results.yaml'

    }

}

}
}
