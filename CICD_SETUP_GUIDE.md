# GitHub CI/CD with Jenkins Pipeline - Setup Guide

## Overview

Automated deployment pipeline: **GitHub → Jenkins → AWS ECR → Ansible → Docker Swarm**

This guide provides complete instructions for setting up the CI/CD pipeline for the BigData Kafka to DynamoDB microservice.

---

## 🏗️ Architecture

```
GitHub Repository (production branch)
    ↓ (webhook trigger)
Jenkins Pipeline
    ↓ (checkout code)
Maven Build + Tests
    ↓ (build JAR)
Docker Image Build
    ↓ (push image)
AWS ECR Repository
    ↓ (deploy via Ansible)
Production Servers (Docker Swarm)
    ↓ (health check)
Deployment Complete ✅
```

---

## 📋 Prerequisites

### 1. Jenkins Server Requirements

**Installed Plugins:**
- Pipeline Plugin
- GitHub Integration Plugin
- Docker Pipeline Plugin
- AWS Steps Plugin
- Ansible Plugin
- Credentials Binding Plugin
- JaCoCo Plugin
- JUnit Plugin

**Verify Installation:**
```bash
# Check Jenkins plugins via CLI
java -jar jenkins-cli.jar -s http://your-jenkins-url/ list-plugins
```

### 2. GitHub Repository Setup

**Required:**
- Repository URL: `git@github.com:julian-razif-figaro/bigdata.git`
- Branch: `production`
- SSH key or Personal Access Token for Jenkins access

### 3. AWS Configuration

**ECR Repository:**
- Registry: `451726692073.dkr.ecr.ap-southeast-1.amazonaws.com`
- Repository: `kafka-to-pv-dynamo-service`
- Region: `ap-southeast-1`

**IAM Permissions Required:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ],
      "Resource": "*"
    }
  ]
}
```

### 4. Production Servers

**Requirements:**
- Docker installed (version 20.10+)
- Docker Swarm initialized
- AWS CLI installed
- Python 3 installed (for Ansible)
- SSH access configured

---

## 🔐 Jenkins Credentials Configuration

### Step 1: Add GitHub SSH Credentials

1. Navigate to: `Jenkins → Manage Jenkins → Manage Credentials`
2. Select domain: `(global)`
3. Click: `Add Credentials`
4. Configure:
   - **Kind:** SSH Username with private key
   - **ID:** `julian-ssh-jenkins`
   - **Username:** `git`
   - **Private Key:** Enter SSH private key for GitHub access
   - **Passphrase:** (if applicable)

### Step 2: Add AWS Credentials

1. Navigate to: `Jenkins → Manage Jenkins → Manage Credentials`
2. Click: `Add Credentials`
3. Configure:
   - **Kind:** AWS Credentials
   - **ID:** `aws-credential`
   - **Access Key ID:** Your AWS access key
   - **Secret Access Key:** Your AWS secret key
   - **Description:** `AWS ECR and DynamoDB access`

### Step 3: Add Ansible SSH Key

1. Navigate to: `Jenkins → Manage Jenkins → Manage Credentials`
2. Click: `Add Credentials`
3. Configure:
   - **Kind:** SSH Username with private key
   - **ID:** `julian-private-key`
   - **Username:** `ubuntu`
   - **Private Key:** Enter SSH private key for production servers
   - **Description:** `Ansible deployment SSH key`

---

## 🌐 GitHub Webhook Configuration

### Step 1: Configure Jenkins GitHub Plugin

1. Navigate to: `Jenkins → Manage Jenkins → Configure System`
2. Find section: `GitHub`
3. Add GitHub Server:
   - **Name:** `GitHub`
   - **API URL:** `https://api.github.com`
   - **Credentials:** (Add GitHub personal access token)
4. Test connection
5. Save configuration

### Step 2: Create GitHub Webhook

1. Go to GitHub repository: `https://github.com/julian-razif-figaro/bigdata`
2. Navigate to: `Settings → Webhooks → Add webhook`
3. Configure:
   - **Payload URL:** `https://your-jenkins-url/github-webhook/`
   - **Content type:** `application/json`
   - **Secret:** (optional, recommended for security)
   - **Events:** Select `Just the push event`
   - **Active:** ✅ Checked
4. Click: `Add webhook`

### Step 3: Verify Webhook

Push a test commit to `production` branch and verify:
- Webhook shows green ✅ in GitHub
- Jenkins job triggers automatically
- Check: `Jenkins → Your Pipeline → Build History`

---

## 📝 Update Inventory File

**Edit:** `inventori.ini`

Replace placeholder IPs with your actual production servers:

```ini
[production]
prod-server-1 ansible_host=10.8.3.184 ansible_user=ubuntu
prod-server-2 ansible_host=10.8.3.8 ansible_user=ubuntu
prod-server-3 ansible_host=10.8.4.90 ansible_user=ubuntu
```

**⚠️ IMPORTANT:** Add `inventori.ini` to `.gitignore` (already configured)

---

## 🔧 Configure AWS Credentials for Application

### Option 1: Use AWS IAM Roles (Recommended)

1. Create IAM role with DynamoDB access policy
2. Attach role to EC2 instances
3. Remove AWS credential environment variables from `Docker-compose.yml`
4. Application will use instance credentials automatically

### Option 2: Use AWS Secrets Manager

1. Create secret in AWS Secrets Manager:
```bash
aws secretsmanager create-secret \
    --name bigdata/dynamo-credentials \
    --region ap-southeast-1 \
    --secret-string '{"accessKey":"YOUR_KEY","secretKey":"YOUR_SECRET"}'
```

2. Update application to fetch credentials from Secrets Manager
3. Grant EC2 instances permission to read secrets

### Option 3: Use Jenkins Credentials (Current Setup)

Update Ansible playbook to inject credentials during deployment:

```yaml
- name: Deploy with AWS credentials from Jenkins
  shell: |
    docker stack deploy \
      -c {{ compose_file }} \
      --with-registry-auth \
      {{ stack_name }}
  environment:
    DYNAMO_ACCESS_KEY: "{{ lookup('env', 'AWS_ACCESS_KEY') }}"
    DYNAMO_SECRET_KEY: "{{ lookup('env', 'AWS_SECRET_KEY') }}"
```

---

## 🚀 Pipeline Execution Flow

### Automatic Trigger

When code is pushed to `production` branch:

1. **GitHub Webhook** → Triggers Jenkins pipeline
2. **SCM Checkout** → Clones repository
3. **Run Tests** → Executes `mvn test jacoco:report`
4. **Build Maven** → Runs `mvn clean install -DskipTests`
5. **Build Docker Image** → Builds and tags image
6. **Push to ECR** → Uploads image to AWS ECR
7. **Deploy with Ansible** → Deploys to Docker Swarm
8. **Health Check** → Verifies deployment success
9. **Notification** → Reports pipeline status

### Manual Trigger

1. Go to Jenkins dashboard
2. Select pipeline: `bigdata-deployment`
3. Click: `Build Now`
4. Monitor: `Console Output`

---

## 🩺 Health Checks and Monitoring

### Application Health Endpoint

```bash
curl http://localhost:8087/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP"
}
```

### Docker Service Status

```bash
# Check service status
docker service ls --filter name=bigdata

# Check service logs
docker service logs bigdata_kafka-to-pv-dynamon-service

# Check individual containers
docker ps --filter name=bigdata
```

### Rollback Strategy

The Ansible playbook includes automatic rollback on health check failure:
1. Detects health check failure
2. Removes failed deployment
3. Restores previous configuration from backup
4. Redeploys previous version
5. Fails pipeline with clear error message

---

## 🔍 Troubleshooting

### Issue: Jenkins Cannot Connect to GitHub

**Solution:**
1. Verify SSH key is correctly configured in Jenkins credentials
2. Test SSH connection: `ssh -T git@github.com`
3. Check GitHub webhook delivery logs
4. Ensure Jenkins has internet access

### Issue: Docker Build Fails

**Solution:**
1. Check Maven build completed successfully
2. Verify JAR file exists: `kafka-to-pv-dynamo-service/target/kafka-to-pv-dynamo-service.jar`
3. Check Dockerfile syntax
4. Review Docker build logs in Jenkins console

### Issue: ECR Push Fails

**Solution:**
1. Verify AWS credentials in Jenkins
2. Check IAM permissions for ECR
3. Test ECR login manually:
```bash
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin 451726692073.dkr.ecr.ap-southeast-1.amazonaws.com
```

### Issue: Ansible Deployment Fails

**Solution:**
1. Verify `inventori.ini` has correct server IPs
2. Test SSH connection to production servers
3. Check Ansible SSH credentials in Jenkins
4. Verify Docker Swarm is initialized on target servers
5. Review Ansible output in Jenkins console

### Issue: Health Check Fails

**Solution:**
1. Check service logs: `docker service logs bigdata_kafka-to-pv-dynamon-service`
2. Verify Spring Boot actuator is enabled
3. Check network connectivity between containers
4. Verify DynamoDB and Kafka connectivity
5. Review application configuration in `Docker-compose.yml`

---

## 📊 Monitoring and Metrics

### Jenkins Build Metrics

- **Build History:** Track success/failure rate
- **Test Results:** JUnit test reports
- **Code Coverage:** JaCoCo reports (target: >88%)
- **Build Time:** Monitor pipeline duration

### Application Metrics

**JMX Monitoring (Port 9090):**
```bash
# Connect using JConsole or VisualVM
jconsole localhost:9090
```

**Actuator Endpoints:**
- Health: `http://localhost:8087/actuator/health`
- Metrics: `http://localhost:8087/actuator/metrics`
- Info: `http://localhost:8087/actuator/info`

---

## 🔒 Security Best Practices

### ✅ Implemented

- ✅ Removed hardcoded AWS credentials from `Docker-compose.yml`
- ✅ Added sensitive files to `.gitignore`
- ✅ Using Jenkins credentials for secret management
- ✅ SSH key-based authentication for Ansible
- ✅ Non-root user in Docker container
- ✅ Health checks for deployment validation
- ✅ Rollback mechanism on failure

### 🔄 Recommended Improvements

1. **Enable Branch Protection**
   - Require PR reviews for `production` branch
   - Require status checks before merge
   - Restrict force pushes

2. **Add Secrets Scanning**
   - Install: GitGuardian or TruffleHog
   - Scan commits for exposed credentials

3. **Enable RBAC in Jenkins**
   - Restrict pipeline access
   - Use role-based permissions

4. **Rotate Credentials Regularly**
   - AWS credentials every 90 days
   - SSH keys annually
   - GitHub tokens every 6 months

5. **Add Vulnerability Scanning**
   - Integrate Snyk or OWASP Dependency Check
   - Scan Docker images before deployment

---

## 🎯 Next Steps

1. **Configure Jenkins Credentials** (all 3 required)
2. **Update inventori.ini** with production server IPs
3. **Set up GitHub Webhook** to trigger builds
4. **Test Pipeline** with a small code change
5. **Monitor First Deployment** and verify health checks
6. **Document AWS IAM Role** setup for production
7. **Add Slack/Email Notifications** for build status

---

## 📞 Support

For issues or questions:
- **Jenkins Admin:** Check Jenkins logs in `/var/log/jenkins/`
- **Application Logs:** `docker service logs bigdata_kafka-to-pv-dynamon-service`
- **Ansible Logs:** Jenkins console output for deployment stage

---

## 📚 Additional Resources

- [Jenkins Pipeline Documentation](https://www.jenkins.io/doc/book/pipeline/)
- [Ansible Docker Module](https://docs.ansible.com/ansible/latest/collections/community/docker/)
- [AWS ECR Documentation](https://docs.aws.amazon.com/ecr/)
- [Docker Swarm Documentation](https://docs.docker.com/engine/swarm/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

**Last Updated:** January 9, 2026  
**Version:** 1.0.0  
**Maintained By:** DevOps Team
