
# Overview
* [Setting up with Amazon ECR](https://docs.aws.amazon.com/AmazonECR/latest/userguide/get-set-up-for-amazon-ecr.html)
* [Using Amazon ECR with the AWS CLI](https://docs.aws.amazon.com/AmazonECR/latest/userguide/getting-started-cli.html)


## AWS CLI installation instructions ([source](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html))

```
$ curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
$ sudo installer -pkg AWSCLIV2.pkg -target /
```

**To test:**

```
$ which aws
/usr/local/bin/aws
$ aws --version
aws-cli/2.4.5 Python/3.8.8 Darwin/18.7.0 botocore/2.4.5
```

**Create Credentials**
* Got to [IAM Console](https://us-east-1.console.aws.amazon.com/iamv2/home#/home) and follow [Set up credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html)

**Authenticate to default registry**

```
aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 147892678753.dkr.ecr.us-west-2.amazonaws.com
```

**Create repo**
```
aws ecr create-repository \
    --repository-name oc-relay \
    --image-scanning-configuration scanOnPush=true
```

**Create Docker image**
```
> docker build -t oc-relay .
```

**Tag Image**
```
docker tag oc-relay:latest 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay:latest
```

**Push Image**
```
docker push 147892678753.dkr.ecr.us-west-2.amazonaws.com/oc-relay:latest
```

**[Delete image or repository](https://docs.aws.amazon.com/AmazonECR/latest/userguide/getting-started-cli.html)**