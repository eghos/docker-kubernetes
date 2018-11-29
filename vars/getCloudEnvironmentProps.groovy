//#!/usr/bin/env groovy
//def call(String property) {
//    def AZURE_DEV_WESTEUROPE_DNS            = "dev-az-svc.westeurope.cloudapp.azure.com"
//    def AZURE_SVC_HOSTNAME                  = "<ENV>-az-svc.<REGION>.cloudapp.azure.com"
//    def GIT_SVC_ACOUNT_EMAIL                = "l-apimgt-u-itsehbg@ikea.com"
//    def GIT_SVC_ACCOUNT_USER                = "l-apimgt-u-itsehbg"
//    def NONPROD_WESTEUROPE_AZRGNAME         = "ipimip-dev-westEurope-rg"
//    def NONPROD_WESTEUROPE_AZACRNAME        = "acrwedevgupuy7"
//    def NONPROD_WESTEUROPE_AZAKSCLUSTERNAME = "akswedevgupuy7"
//    def PROD_WESTEUROPE_AZRGNAME            = "ipimip-ppe-westEurope-rg"
//    def PROD_WESTEUROPE_AZACRNAME           = "acrweppeafsibk"
//    def PROD_WESTEUROPE_AZAKSCLUSTERNAME    = "aksweppeafsibk"
//    return property
//}
//def test_var = "qwerty"
//
//def getTest(){
//    return "${test_var}"
//}
class getCloudEnvironmentProps{
    def AZURE_DEV_WESTEUROPE_DNS            = "dev-az-svc.westeurope.cloudapp.azure.com"

    def getAzureDevWesteuropeDns(){
        return AZURE_DEV_WESTEUROPE_DNS
    }
}