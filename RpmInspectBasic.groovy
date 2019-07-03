#!groovy

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
        def json_message = readJSON text: env.CI_MESSAGE
        env.TARGET_ENVR = "${json_message['name']}-${json_message['version']}-${json_message['release']}"

        // Set our message topic, properties, and content
        //messageFields = packagepipelineUtils.setMessageFields("package.test.functional.queued", env.TARGET_ENVR)

        // Send message org.centos.prod.ci.pipeline.allpackages.package.test.functional.queued on fedmsg
        //pipelineUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

        // Set stage specific vars
        //packagepipelineUtils.setStageEnvVars(currentStage)

        // Set our message topic, properties, and content
        //messageFields = packagepipelineUtils.setMessageFields("package.test.functional.running", env.TARGET_ENVR)

        executeInContainer(currentStage, "package-checks", "/tmp/run-rpminspect.sh")

        // Set our message topic, properties, and content
        //messageFields = packagepipelineUtils.setMessageFields("package.test.functional.complete", env.TARGET_ENVR)

        // Send message org.centos.prod.ci.pipeline.allpackages.package.test.functional.complete on fedmsg
        //pipelineUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

    }
    stage("archive output"){

        archiveArtifacts artifacts: 'run-rpminspect/logs/rpminspect.json'
        archiveArtifacts artifacts: 'run-rpminspect/logs/results.yaml'

    }
}
    }
