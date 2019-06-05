pipelineJob('Fedora Build Check'){
    description 'Job to run checks on Fedora builds'

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
        stringParam('CI_MESSAGE', '{}', 'fedora-fedmsg')
    }


    definition {
        cps {
            //script("print env.CI_MESSAGE")
            script(readFileFromWorkspace("RpmInspectBasic.groovy"))
        }
    }
}
