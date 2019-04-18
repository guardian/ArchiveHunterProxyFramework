#!/usr/bin/env python3

import boto3
import os.path
import argparse
from functools import reduce
from datetime import datetime

refs_to_find = ["RequestLambda","TranscoderReplyLambda", "SweeperLambda"]
jars_for_ref = ["ProxyRequestLambda/target/scala-2.12/proxyRequestLambda.jar",
                "TranscoderReplyLambda/target/scala-2.12/transcoderReplyLambda.jar",
                "SweeperLambda/target/scala-2.12/sweeperLambda.jar"]
upload_path_selector = ["archivehunter-proxyrequest-lambda","archivehunter-transcoderreply-lambda", "archivehunter-sweeper-lambda"]

regional_buckets = {
    "eu-west-1": "gnm-multimedia-rr-deployables",
    "us-east-1": "gnm-multimedia-use1-deployables",
    "ap-southeast-2": "gnm-multimedia-aps1-deployables"
}


def upload_path_for(stack_params, upload_path_selector):
    return "{0}/{1}/{2}".format(stack_params['Stack'],stack_params['Stage'],upload_path_selector)


def upload_jar(local_path, bucket_name, bucket_path):
    """
    upload the jarfile to S3
    :param local_path:
    :param bucket_name:
    :param bucket_path:
    :return: path that the file was uploaded to
    """
    full_path = os.path.join(bucket_path, os.path.basename(local_path))
    print("Uploading {0} to s3://{1}/{2}...".format(local_path, bucket_name, full_path))
    with open(local_path, "rb") as file_ref:
        bucket = s3.Bucket(bucket_name)
        bucket.put_object(
            ACL='private',
            Body=file_ref,
            Key=full_path,
        )
    return full_path


def publish_version(function_name, bucket_name, bucket_path):
    """
    publish a new version of the lambda function for the given code
    :param function_name:
    :param bucket_name:
    :param bucket_path:
    :return:
    """
    print("Publishing new version of {0}".format(function_name))
    lambda_client.update_function_code(
        FunctionName=function_name,
        S3Bucket=bucket_name,
        S3Key=bucket_path,
        Publish=True
    )
    #lambda_client.tag_resource(Resource=function_name, Tags={'Updated': datetime.now().strftime("%Y%m%d%H%M")})


def locate_functions(stack_name):
    """
    gets the actual names of the lambda functions from the given stack, based on our list of logical names
    :param stack_name:
    :param region:
    :return:
    """
    resource_info = map(lambda entry: cloudformation.describe_stack_resource(StackName=stack_name, LogicalResourceId=entry), refs_to_find)
    # for r in resource_info:
    #     print(r['StackResourceDetail']['PhysicalResourceId'])
    # return list(map(lambda entry: entry['StackResourceDetail']['PhysicalResourceId'], list(resource_info)))
    return [r['StackResourceDetail']['PhysicalResourceId'] for r in resource_info]


def locate_stack(stack_name):
    """
    get the stack info for the provided stack. An AmazonCloudFormationException is raised if the stack does not exist
    :param stack_name:
    :param region:
    :return:
    """
    result = cloudformation.describe_stacks(StackName=stack_name)
    return result['Stacks'][0]


def get_stack_parameters(stack_name):
    stackinfo = locate_stack(stack_name)
    return reduce(lambda rtn, entry: dict(rtn, **{entry['ParameterKey']: entry['ParameterValue']}), stackinfo['Parameters'], {})


## START MAIN
parser = argparse.ArgumentParser()
parser.add_argument("--stackname","-s",help="Name of the deployed Cloudformation stack we are updating")
parser.add_argument("--region","-r",help="Region that the stack is deployed in")
parser.add_argument("--initial","-i",
                    action="store_true",
                    default=False,
                    help="Upload jars but do not attempt to update lambda functions. Use this prior to deploying the cloudformation.")
parser.add_argument("--stackparam",help="stack identifier tag when performing intial upload")
parser.add_argument("--stageparam",help="stage identifier tag when performing intial upload")

opts = parser.parse_args()

s3 = boto3.resource("s3", region_name=opts.region)
lambda_client = boto3.client("lambda", region_name=opts.region)
cloudformation = boto3.client("cloudformation", region_name=opts.region)

if not opts.initial:
    params = get_stack_parameters(opts.stackname)
    print("Found stack parameters {0}".format(params))

    function_names = locate_functions(opts.stackname)
    print("Found functions to update: {0}".format(function_names))
    if len(function_names)!=len(refs_to_find):
        print("ERROR: could not find correct number of functions to update!")
        exit(1)
else:
    params = {
        "Stack": opts.stackparam,
        "Stage": opts.stageparam
    }
    function_names = []

print("---------------------------------------------")
for idx in range(0, len(refs_to_find)):
    uploaded_path = upload_jar(jars_for_ref[idx], regional_buckets[opts.region], upload_path_for(params,upload_path_selector[idx]))
    print("Uploaded JAR to {0}".format(uploaded_path))
    if not opts.initial:
        publish_version(function_names[idx],regional_buckets[opts.region],uploaded_path)
        print("{0} has been updated".format(function_names[idx]))
    print("---------------------------------------------")

print("\nUpdate completed.")
