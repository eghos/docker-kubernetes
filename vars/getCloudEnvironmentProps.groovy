#!/usr/bin/env groovy

def call(String property) {
//    Properties props = new Properties()
//    File propsFile = new File('/CloudPlatform.properties')
//    props.load(propsFile.newDataInputStream())
//
//    return props.getProperty(property)
    String AZURE_DEV_WESTEUROPE_DNS = "dev-az-svc.westeurope.cloudapp.azure.com"
    String GIT_SVC_ACOUNT_EMAIL = "l-apimgt-u-itsehbg@ikea.com"
    String GIT_SVC_ACCOUNT_USER = "l-apimgt-u-itsehbg"

    return property
}