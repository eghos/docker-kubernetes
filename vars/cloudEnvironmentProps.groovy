//#!/usr/bin/env groovy
class cloudEnvironmentProps{
    def AZURE_DEV_WESTEUROPE_DNS            = "dev-az-svc.westeurope.cloudapp.azure.com"
    def AZURE_TEST_WESTEUROPE_DNS           = "test-az-svc.westeurope.cloudapp.azure.com"
    def AZURE_PPE_WESTEUROPE_DNS            = "ppe-az-svc.westeurope.cloudapp.azure.com"
    def AZURE_PROD_WESTEUROPE_DNS           = "prod-az-svc.westeurope.cloudapp.azure.com"

    def NONPROD_WESTEUROPE_AZRGNAME         = "ipimip-dev-westEurope-rg"
    def NONPROD_WESTEUROPE_AZACRNAME        = "acrwedevgupuy7"
    def NONPROD_WESTEUROPE_AZAKSCLUSTERNAME = "akswedevgupuy7"

    def PROD_WESTEUROPE_AZRGNAME            = "ipimip-ppe-westEurope-rg"
    def PROD_WESTEUROPE_AZACRNAME           = "acrweppeafsibk"
    def PROD_WESTEUROPE_AZAKSCLUSTERNAME    = "aksweppeafsibk"

    def AZURE_SVC_HOSTNAME                  = "<ENV>-az-svc.<REGION>.cloudapp.azure.com"
    def GIT_SVC_ACOUNT_EMAIL                = "l-apimgt-u-itsehbg@ikea.com"
    def GIT_SVC_ACCOUNT_USER                = "l-apimgt-u-itsehbg"

    def getAzureDevWesteuropeDns(){
        return AZURE_DEV_WESTEUROPE_DNS
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
}