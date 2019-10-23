/**
 * A class of methods used in the Jenkinsfile pipeline.
 * These methods are wrappers around methods in the ci-pipeline library.
 */

class buildCheckUtils implements Serializable {

    MAIN_TOPIC = ''

    def setupEnvVars(String main_topic) {
        this.MAIN_TOPIC = main_topic
    }

    /**
    * General function to check existence of a file
    * @param fileLocation
    * @return boolean
    */
    def fileExists(String fileLocation) {
        def status = false
        try {
            def contents = readFile(fileLocation)
            status = true
        } catch(e) {
            println "file not found: ${fileLocation}"
        }

        return status
    }


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
        //topic = "${MAIN_TOPIC}.ci.${messageType}"
        topic = "${this.MAIN_TOPIC}.ci.${messageType}"
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
}
