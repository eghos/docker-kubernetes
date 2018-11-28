#!/usr/bin/env groovy

def call(String property) {
    Properties props = new Properties()
    File propsFile = new File('/vars/CloudPlatform.properties')
    props.load(propsFile.newDataInputStream())

    return props.getProperty(property)
}