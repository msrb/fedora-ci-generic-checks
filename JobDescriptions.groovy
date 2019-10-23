pipelineJob('rpminspect-simple'){

    description 'Job to run checks on Fedora builds'

    // default so we don't need to wait around for builds to happen
    def CANNED_CI_MESSAGE = '{"build_id":1404149,"old":0,"name":"mirrormanager2","task_id":38507123,"attribute":"state","request":["git+https://src.fedoraproject.org/rpms/mirrormanager2.git#763ae90d00b4735a32c96407103e4a4e31360de6","f30-candidate",{}],"instance":"primary","epoch":null,"version":"0.11","owner":"adrian","new":1,"release":"1.fc30"}'
    // use a smaller build for faster turnaround
    //def CANNED_CI_MESSAGE = '{"build_id":1288823,"old":0,"name":"sssd","task_id":35594360,"attribute":"state","request":["git+https://src.fedoraproject.org/rpms/sssd.git#80b558654cf4cbb72c267b82ff9395cb531dab93","f30-candidate",{}],"instance":"primary","epoch":null,"version":"2.2.0","owner":"mzidek","new":1,"release":"1.fc30"}'


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
        // This is for apps.ci.centos.org
        stringParam('DOCKER_REPO_URL', '172.30.254.79:5000', 'Docker Repo URL')
        // local dev
        //stringParam('DOCKER_REPO_URL', 'docker-registry.default.svc:5000', 'Docker Repo URL')
        stringParam('OPENSHIFT_NAMESPACE', 'fedora-package-checks', 'OpenShift Namespace')
        stringParam('OPENSHIFT_SERVICE_ACCOUNT', 'fedora-check-jenkins', 'OpenShift Service Account')
    }


    definition {
        cps {
            //script("print env.CI_MESSAGE")
            script(readFileFromWorkspace("RpmInspectBasic.groovy"))
        }
    }
}
