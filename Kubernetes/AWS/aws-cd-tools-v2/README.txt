Credentials file contains the user details of the user that has been set up in the AWS Account to communicate using CLI.
Config file contains

Pre-requisites:
Python  (version: 2.7.15 +)
aws cli (version: 1.16.19 +)
kubectl (version: 1.13.1 +)

Instructions:

1. Place the 2 files (credentials & config) in the ~/.aws directory.
If directory does not exists then create it.

$ mkdir -p ~/.aws
$ cp credentials ~/.aws/credentials
$ cp config ~/.aws/config

2. To interact with an AWS account use profile environment variable.

Dev account:
$ export AWS_PROFILE=eks@ikea-dev

Test account:
$ export AWS_PROFILE=eks@ikea-test

PPE Account:
$ export AWS_PROFILE=eks@ikea-preprod

Prod account:
$ export AWS_PROFILE=eks@

3. Use the aws cli to create or update kubeconfig:

Fx: create a kubeconfig for the EKS cluster named "cluster1"

aws eks update-kubeconfig --kubeconfig awskubeconfig --name cluster1
Note: aws-authenticator should be added to PATH or add ./ infront of the name

Dynamically get cluster name using tags:

e.g.

$ aws ssm get-parameter --name /ipimip/cluster/cluster1/name --query 'Parameter.Value' --output text

To get the cluster name dynamically, the approach in AWS would be to use the following command:

`aws ssm get-parameter --name /ipimip/cluster/<the_cluster_tag>/name --query 'Parameter.Value' --output text`

Notice the <the_cluster_tag> this should be replaced with the cluster tag you have for Azure. So if the Cluster tag is "default" it will look like this:

`aws ssm get-parameter --name /ipimip/cluster/default/name --query 'Parameter.Value' --output text`

4. Now you can interact with the EKS cluster using normal kubectl and reference the kubeconfig file just created.

kubectl --kubeconfig awskubeconfig get nodes