#!/usr/bin/groovy
package org.fedoraproject.ci


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
