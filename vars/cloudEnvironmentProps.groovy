//#!/usr/bin/env groovy
class cloudEnvironmentProps{
    def APIARY_IO_TOKEN                     = "890e555a1d3107539c130f23b9494155"
    def APIARY_DREDD_TOKEN                  = "ce16ad7641d98a84d231ebb0b1a14292"

    def PROD_WESTEUROPE_AZRGNAME            = "ipimip-prod-westeurope-rg"
    def PPE_WESTEUROPE_AZRGNAME             = "ipimip-ppe-westEurope-rg"
    def PPE_CENTRALUS_AZRGNAME              = "ipimip-ppe-centralUS-rg"
    def TEST_WESTEUROPE_AZRGNAME            = "ipimip-test-westEurope-rg"
    def DEV_WESTEUROPE_AZRGNAME             = "ipimip-dev-westEurope-rg"

    def PROD_WESTEUROPE_AZACRNAME           = "acrweprody3qy3j"

    def AWS_CONTAINER_REPOSITORY_URL        = "318063795105.dkr.ecr.eu-west-1.amazonaws.com"

    def AZURE_SVC_HOSTNAME                  = "<ENV>.<REGION>.svc.hip.red.cdtapps.com"
    def AWS_SVC_HOSTNAME                    = "<ENV>.<REGION>.svc.hip.red.cdtapps.com"
    def GIT_SVC_ACOUNT_EMAIL                = "l-apimgt-u-itsehbg@ikea.com"
    def GIT_SVC_ACCOUNT_USER                = "l-apimgt-u-itsehbg"
    def NPM_NEXUS_REPOSITORY_URL            = "https://nexus.hip.red.cdtapps.com/repository/npm-internal/"
    def DOCKER_IMAGE_ORG                    = "apimgt"

    def AZURE_PROD_SUBSCRIPTION_ID          = "4c58a8b3-26bd-4206-a3ca-6d1fac5d0ed5"
    def AZURE_LOWER_ENV_SUBSCRIPTION_ID     = "6795aaca-7ddd-4af7-ae6d-a984bf8d7744"

    def OPENSHIFT_SERVICE_ACCOUNT_TOKEN     = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJvY3AtcGlwZWxpbmVzLWlwaW0taXAiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlY3JldC5uYW1lIjoiamVua2lucy10b2tlbi1tMjVkZyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJqZW5raW5zIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQudWlkIjoiM2EzNjhjOTktZTk4MS0xMWU4LTgyZTQtMDA1MDU2ODUxMmM3Iiwic3ViIjoic3lzdGVtOnNlcnZpY2VhY2NvdW50Om9jcC1waXBlbGluZXMtaXBpbS1pcDpqZW5raW5zIn0.DUM8sW_mhH67NEHSa854qyrdSwmDWPqqCw6yCF5Wg1vkWM3wgpndfHMHbi5ULW2RkghqwrBzO0RCAFAcOW38AwGoqkcOtmlEgBQN5z_9qoXcQw00ze8EkPz0paVDV4Qw1NJ6iI0Z6mYlZNV8OdUKkySPvu4kRDJdqNL20xBnJLkc1Zx2Rh_OfJXtcSutqm2FHBEIzadM_kAezhr_4Awj4YP5aLdosQUqYHi9C4UBdggTrTQpYV-2A3LbNZ0VHYHqG6y6k5XD8hPKOFZFQvlU1jATjk5FG50KCbqyIzUAeoPq7tGI26rTsXYLS_d4sW4-BMwRGYbDzFPIBXrSXoXWng"

    def getApiaryIoToken(){
        return APIARY_IO_TOKEN
    }
    def getApiaryDreddToken(){
        return APIARY_DREDD_TOKEN
    }
    def getProdWesteuropeAzRgName(){
        return PROD_WESTEUROPE_AZRGNAME
    }
    def getProdWesteuropeAzAcrName(){
        return PROD_WESTEUROPE_AZACRNAME
    }
    def getAzureSvcHostname(){
        return AZURE_SVC_HOSTNAME
    }
    def getAwsSvcHostname(){
        return AWS_SVC_HOSTNAME
    }
    def getGitSvcAccountEmail(){
        return GIT_SVC_ACOUNT_EMAIL
    }
    def getGitSvcAccountUser(){
        return GIT_SVC_ACCOUNT_USER
    }
    def getNpmNexusRepositoryUrl(){
        return NPM_NEXUS_REPOSITORY_URL
    }
    def getDockerImageOrg(){
        return DOCKER_IMAGE_ORG
    }
    def getAzureProdSubscriptionId(){
        return AZURE_PROD_SUBSCRIPTION_ID
    }
    def getAzureLowerEnvSubscriptionId(){
        return AZURE_LOWER_ENV_SUBSCRIPTION_ID
    }
    def getAwsContainerRepositoryUrl(){
        return AWS_CONTAINER_REPOSITORY_URL
    }
    def getOpenshiftServiceAccountToken(){
        return OPENSHIFT_SERVICE_ACCOUNT_TOKEN
    }
}