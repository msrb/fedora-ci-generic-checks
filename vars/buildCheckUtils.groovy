import org.fedoraproject.ci.BuildCheckUtils

/**
 * A class of methods used in the Jenkinsfile pipeline.
 * These methods are wrappers around methods in the ci-pipeline library.
 */

class buildCheckUtils implements Serializable {

    def buildCheckUtils = new BuildCheckUtils()

    /**
    * General function to check existence of a file
    * @param fileLocation
    * @return boolean
    */
    def fileExists(String fileLocation) {
        buildCheckUtils.fileExists(fileLocation)
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
        buildCheckUtils.executeInContainer(stageName, containerName, script, vars)
    }

    /**
    * Library to set message fields to be published
    * @param messageType: ${MAIN_TOPIC}.ci.pipeline.allpackages.<defined-in-README>
    * @param artifact ${MAIN_TOPIC}.ci.pipeline.allpackages-${artifact}.<defined-in-README>
    * @return
    */
    def setMessageFields(String messageType, String artifact) {
        buildCheckUtils.setMessageFields(messageType, artifact)
    }

    /**
    * Library to send message
    * @param msgProps - Message Properties - empty string for fedmsg
    * @param msgContent - Message content in map form
    * @return
    */
    def sendMessage(String msgTopic, String msgProps, String msgContent) {
        buildCheckUtils.sendMessage(msgTopic, msgProps, msgContent)
    }
}
