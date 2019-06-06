pipelineJob('Fedora Build Check'){
    description 'Job to run checks on Fedora builds'

    // default so we don't need to wait around for builds to happen
    def CANNED_CI_MESSAGE = '{"commit":{"username":"eseyman","stats":{"files":{"perl-Net-FTPSSL.spec":{"deletions":2,"additions":5,"lines":7},".gitignore":{"deletions":0,"additions":1,"lines":1},"sources":{"deletions":1,"additions":1,"lines":2}},"total":{"deletions":3,"files":3,"additions":7,"lines":10}},"name":"Emmanuel Seyman","rev":"c1c7de158fa72de5bd279daaaac9f75d0b3e65cd","namespace":"rpms","agent":"eseyman","summary":"Update to 0.40","repo":"perl-Net-FTPSSL","branch":"master","seen":false,"path":"/srv/git/repositories/rpms/perl-Net-FTPSSL.git","message":"Update to 0.40\n","email":"emmanuel@seyman.fr"},"topic":"org.fedoraproject.prod.git.receive"}'

    // Audit file for all messages sent.
    msgAuditFile = "messages/message-audit.json"

    // Number of times to keep retrying to make sure message is ingested
    // by datagrepper
    fedmsgRetryCount = 120

    triggers{
        ciBuildTrigger{
            noSquash(true)
            providers {
                providerDataEnvelope {
                    providerData {
                fedmsgSubscriber{
                    name("fedora-fedmsg")
                    overrides {
                        topic("org.fedoraproject.prod.buildsys.build.state.change")
                    }
                    checks {
                        msgCheck {
                            field("new")
                            expectedValue("1|CLOSED")
                        }
                        msgCheck {
                            field("release")
                            expectedValue(".*fc.*")
                        }
                        msgCheck {
                            field("instance")
                            expectedValue("primary")
                        }
                    }
                }
                    }}
            }
        }
    }
    parameters{
        stringParam('CI_MESSAGE', CANNED_CI_MESSAGE, 'fedora-fedmsg')
    }


    definition {
        cps {
            //script("print env.CI_MESSAGE")
            script(readFileFromWorkspace("RpmInspectBasic.groovy"))
        }
    }
}
