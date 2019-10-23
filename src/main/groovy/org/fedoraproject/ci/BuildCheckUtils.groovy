#!/usr/bin/groovy
package org.fedoraproject.ci

/**
 * A class of methods used in the Jenkinsfile pipeline.
 * These methods are wrappers around methods in the ci-pipeline library.
 */
class BuildCheckUtils implements Serializable {

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
}
