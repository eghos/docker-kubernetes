//#!/usr/bin/env groovy
class cloudEnvironmentProps{
    def APIARY_IO_TOKEN                     = "890e555a1d3107539c130f23b9494155"
    def AZURE_DEV_WESTEUROPE_DNS            = "ppe-az-svc.centralus.cloudapp.azure.com"
    def AZURE_TEST_WESTEUROPE_DNS           = "test-az-svc.westeurope.cloudapp.azure.com"
    def AZURE_PPE_WESTEUROPE_DNS            = "ppe-az-svc.westeurope.cloudapp.azure.com"
    def AZURE_PROD_WESTEUROPE_DNS           = "prod-az-svc.westeurope.cloudapp.azure.com"
    def AZURE_PPE_CENTRALUS_DNS             = "ppe-az-svc.centralus.cloudapp.azure.com"

//    def NONPROD_WESTEUROPE_AZRGNAME         = "ipimip-dev-westEurope-rg"
//    def NONPROD_WESTEUROPE_AZACRNAME        = "acrwedevgupuy7"
//    def NONPROD_WESTEUROPE_AZAKSCLUSTERNAME = "akswedevgupuy7"

//    def NONPROD_WESTEUROPE_AZRGNAME         = "ipimip-test-westEurope-rg"
//    def NONPROD_WESTEUROPE_AZACRNAME        = "acrwetest5i4vqq"
//    def NONPROD_WESTEUROPE_AZAKSCLUSTERNAME = "akswetest5i4vqq"

    def NONPROD_WESTEUROPE_AZRGNAME         = "ipimip-ppe-westEurope-rg"
    def NONPROD_WESTEUROPE_AZACRNAME        = "acrweppe01"
    def NONPROD_WESTEUROPE_AZAKSCLUSTERNAME = "aksweppe01"

//    def NONPROD_WESTEUROPE_AZRGNAME         = "ipimip-prod-westEurope-rg"
//    def NONPROD_WESTEUROPE_AZACRNAME        = "acrweprod01"
//    def NONPROD_WESTEUROPE_AZAKSCLUSTERNAME = "aksweprod01"

//    def NONPROD_WESTEUROPE_AZRGNAME         = "ipimip-ppe-CentralUS-rg"
//    def NONPROD_WESTEUROPE_AZACRNAME        = "acrcusppe01"
//    def NONPROD_WESTEUROPE_AZAKSCLUSTERNAME = "akscusppe01"

    def PROD_WESTEUROPE_AZRGNAME            = "ipimip-ppe-westEurope-rg"
    def PROD_WESTEUROPE_AZACRNAME           = "acrweppeafsibk"
    def PROD_WESTEUROPE_AZAKSCLUSTERNAME    = "aksweppeafsibk"

    def AZURE_SVC_HOSTNAME                  = "<ENV>-az-svc.<REGION>.cloudapp.azure.com"
    def GIT_SVC_ACOUNT_EMAIL                = "l-apimgt-u-itsehbg@ikea.com"
    def GIT_SVC_ACCOUNT_USER                = "l-apimgt-u-itsehbg"
    def NPM_NEXUS_REPOSITORY_URL            = "https://nexus.hip.red.cdtapps.com/repository/npm-internal/"

    def getApiaryIoToken(){
        return APIARY_IO_TOKEN
    }
    def getAzureDevWesteuropeDns(){
        return AZURE_DEV_WESTEUROPE_DNS
    }
    def getAzureTestWesteuropeDns(){
        return AZURE_TEST_WESTEUROPE_DNS
    }
    def getAzurePpeWesteuropeDns(){
        return AZURE_PPE_WESTEUROPE_DNS
    }
    def getAzureProdWesteuropeDns(){
        return AZURE_PROD_WESTEUROPE_DNS
    }
    def getAzurePpeCentralUsDns(){
        return AZURE_PPE_CENTRALUS_DNS
    }
    def getNonProdWesteuropeAzRgName(){
        return NONPROD_WESTEUROPE_AZRGNAME
    }
    def getNonProdWesteuropeAzAcrName(){
        return NONPROD_WESTEUROPE_AZACRNAME
    }
    def getNonProdWesteuropeAzAksClusterName(){
        return NONPROD_WESTEUROPE_AZAKSCLUSTERNAME
    }
    def getProdWesteuropeAzRgName(){
        return PROD_WESTEUROPE_AZRGNAME
    }
    def getProdWesteuropeAzAcrName(){
        return PROD_WESTEUROPE_AZACRNAME
    }
    def getProdWesteuropeAzAksClusterName(){
        return PROD_WESTEUROPE_AZAKSCLUSTERNAME
    }

    def getAzureSvcHostname(){
        return AZURE_SVC_HOSTNAME
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
}