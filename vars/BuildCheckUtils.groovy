import org.fedoraproject.ci.BuildCheckUtils

/**
 * A class of methods used in the Jenkinsfile pipeline.
 * These methods are wrappers around methods in the ci-pipeline library.
 */
class buildCheckUtils implements Serializable {

    def buildCheckUtils = new BuildCheckUtils

    /**
    * General function to check existence of a file
    * @param fileLocation
    * @return boolean
    */
    def fileExists(String fileLocation) {
        buildCheckUtils.fileExists(fileLocation)
    }
}
