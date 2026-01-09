# Jenkins Pipeline Quick Reference

## 🚀 Quick Start Commands

### Run Pipeline Manually
```bash
# Via Jenkins CLI
java -jar jenkins-cli.jar -s http://your-jenkins-url/ build bigdata-deployment

# Via curl (with crumb for CSRF protection)
CRUMB=$(curl -u "user:token" 'http://jenkins-url/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)')
curl -u "user:token" -H "$CRUMB" -X POST http://jenkins-url/job/bigdata-deployment/build
```

### Check Build Status
```bash
# Get latest build status
curl -u "user:token" http://jenkins-url/job/bigdata-deployment/lastBuild/api/json
```

---

## 📋 Pipeline Stages Overview

| Stage | Duration | Actions | Artifacts |
|-------|----------|---------|-----------|
| SCM Checkout | ~30s | Clone from GitHub production branch | Source code |
| Run Tests | ~2-5min | Maven test + JaCoCo coverage | Test reports, coverage reports |
| Build Maven | ~3-7min | Maven clean install (skip tests) | JAR file (4 modules) |
| Build Docker Image | ~2-4min | Docker build + tag + push to ECR | Docker image |
| Deploy with Ansible | ~1-3min | Ansible playbook execution | Deployed services |
| Health Check | ~30-60s | HTTP health endpoint verification | Health status |

**Total Pipeline Time:** ~10-20 minutes

---

## 🔐 Required Jenkins Credentials

| Credential ID | Type | Purpose | Used In |
|---------------|------|---------|---------|
| `julian-ssh-jenkins` | SSH Private Key | GitHub repository access | SCM Checkout stage |
| `aws-credential` | AWS Credentials | ECR push/pull + DynamoDB access | Build Docker Image stage |
| `julian-private-key` | SSH Private Key | Ansible server access | Deploy with Ansible stage |

---

## 🌳 Environment Variables

Available in pipeline:

```groovy
AWS_REGION = 'ap-southeast-1'
ECR_REGISTRY = '451726692073.dkr.ecr.ap-southeast-1.amazonaws.com'
IMAGE_NAME = 'kafka-to-pv-dynamo-service'
IMAGE_TAG = "${env.BUILD_NUMBER}"
MAVEN_OPTS = '-Xmx2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1'
```

---

## 🐳 Docker Commands Reference

### During Build
```bash
# ECR Login (automated in pipeline)
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin 451726692073.dkr.ecr.ap-southeast-1.amazonaws.com

# Build image
docker-compose -f docker-compose.yml build

# Tag with build number
docker tag 451726692073.dkr.ecr.ap-southeast-1.amazonaws.com/kafka-to-pv-dynamo-service:latest \
            451726692073.dkr.ecr.ap-southeast-1.amazonaws.com/kafka-to-pv-dynamo-service:${BUILD_NUMBER}

# Push to ECR
docker-compose -f docker-compose.yml push
docker push 451726692073.dkr.ecr.ap-southeast-1.amazonaws.com/kafka-to-pv-dynamo-service:${BUILD_NUMBER}
```

### Post-Deployment Verification
```bash
# Check service status on production servers
docker service ls --filter name=bigdata

# View service logs
docker service logs -f bigdata_kafka-to-pv-dynamon-service

# Check individual container health
docker ps --filter name=bigdata --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Inspect service details
docker service inspect bigdata_kafka-to-pv-dynamon-service
```

---

## 🎯 Ansible Commands Reference

### Manual Deployment
```bash
# Deploy to production
ansible-playbook -i inventori.ini ansible-deploy.yml -e "image_tag=latest"

# Deploy specific image tag
ansible-playbook -i inventori.ini ansible-deploy.yml -e "image_tag=123"

# Dry run (check mode)
ansible-playbook -i inventori.ini ansible-deploy.yml --check

# Verbose output
ansible-playbook -i inventori.ini ansible-deploy.yml -vvv
```

### Inventory Management
```bash
# List all hosts
ansible all -i inventori.ini --list-hosts

# Ping production servers
ansible production -i inventori.ini -m ping

# Check disk space
ansible production -i inventori.ini -m shell -a "df -h"

# Check Docker version
ansible production -i inventori.ini -m shell -a "docker version"
```

---

## 🩺 Health Check Endpoints

### Application Health
```bash
# Primary health check
curl http://localhost:8087/actuator/health

# Detailed health with components
curl http://localhost:8087/actuator/health/details

# Readiness probe
curl http://localhost:8087/actuator/health/readiness

# Liveness probe
curl http://localhost:8087/actuator/health/liveness
```

### Expected Response (Healthy)
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

---

## 🔄 Rollback Procedures

### Automatic Rollback (Handled by Ansible)
- Triggered automatically if health check fails
- Restores previous Docker Compose configuration
- Redeploys last known good version

### Manual Rollback
```bash
# Method 1: Via Jenkins (Rebuild previous successful build)
1. Go to Jenkins → bigdata-deployment → Build History
2. Find last successful build
3. Click "Rebuild"

# Method 2: Via Ansible with specific image tag
ansible-playbook -i inventori.ini ansible-deploy.yml -e "image_tag=PREVIOUS_BUILD_NUMBER"

# Method 3: Via Docker Swarm on production servers
ssh ubuntu@prod-server-1
docker service update \
  --image 451726692073.dkr.ecr.ap-southeast-1.amazonaws.com/kafka-to-pv-dynamo-service:PREVIOUS_TAG \
  bigdata_kafka-to-pv-dynamon-service
```

---

## 📊 Monitoring Commands

### Jenkins Build Metrics
```bash
# Get last 10 build results
curl -u "user:token" http://jenkins-url/job/bigdata-deployment/api/json?tree=builds[number,result,duration]

# Get test results
curl -u "user:token" http://jenkins-url/job/bigdata-deployment/lastBuild/testReport/api/json

# Get coverage report
curl -u "user:token" http://jenkins-url/job/bigdata-deployment/lastBuild/jacoco/api/json
```

### Application Metrics
```bash
# JVM metrics
curl http://localhost:8087/actuator/metrics/jvm.memory.used
curl http://localhost:8087/actuator/metrics/jvm.threads.live
curl http://localhost:8087/actuator/metrics/process.cpu.usage

# HTTP metrics
curl http://localhost:8087/actuator/metrics/http.server.requests

# Custom metrics (if configured)
curl http://localhost:8087/actuator/metrics/kafka.consumer.records.consumed
curl http://localhost:8087/actuator/metrics/dynamodb.requests
```

---

## 🛠️ Troubleshooting Commands

### Pipeline Failures

#### Stage: SCM Checkout
```bash
# Test GitHub SSH connection
ssh -T git@github.com

# Verify credentials
# Jenkins → Credentials → julian-ssh-jenkins → Test Connection
```

#### Stage: Run Tests
```bash
# Run tests locally
mvn test

# Check test reports
cat kafka-to-pv-dynamo-service/target/surefire-reports/*.txt

# Check coverage
mvn jacoco:report
open kafka-to-pv-dynamo-service/target/site/jacoco/index.html
```

#### Stage: Build Maven
```bash
# Build locally with verbose output
mvn clean install -X

# Check dependencies
mvn dependency:tree

# Validate POM
mvn validate
```

#### Stage: Build Docker Image
```bash
# Test Docker build locally
cd kafka-to-pv-dynamo-service
docker build -t test-image .

# Check JAR exists
ls -lh target/kafka-to-pv-dynamo-service.jar

# Verify Dockerfile syntax
docker build --check -f Dockerfile .
```

#### Stage: Deploy with Ansible
```bash
# Test Ansible connection
ansible production -i inventori.ini -m ping

# Run playbook in check mode
ansible-playbook -i inventori.ini ansible-deploy.yml --check

# Debug Ansible
ansible-playbook -i inventori.ini ansible-deploy.yml -vvv
```

### Production Server Issues
```bash
# SSH to production server
ssh -i ~/.ssh/julian-private-key.pem ubuntu@prod-server-1

# Check Docker daemon
sudo systemctl status docker

# Check Swarm status
docker node ls

# Check available disk space
df -h

# Check memory usage
free -h

# Check service logs
docker service logs --tail 100 bigdata_kafka-to-pv-dynamon-service

# Check container logs
docker ps -a
docker logs CONTAINER_ID
```

---

## 🔔 Notification Integration (Optional)

### Slack Notifications

Add to Jenkinsfile `post` section:
```groovy
post {
    success {
        slackSend(
            color: 'good',
            message: "✅ Deployment successful: ${env.JOB_NAME} #${env.BUILD_NUMBER}\nImage: ${IMAGE_TAG}"
        )
    }
    failure {
        slackSend(
            color: 'danger',
            message: "❌ Deployment failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}\nCheck: ${env.BUILD_URL}"
        )
    }
}
```

### Email Notifications

Add to Jenkinsfile `post` section:
```groovy
post {
    always {
        emailext(
            subject: "Pipeline ${currentBuild.result}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            body: """
                Build: ${env.BUILD_URL}
                Status: ${currentBuild.result}
                Duration: ${currentBuild.durationString}
            """,
            to: 'devops@example.com'
        )
    }
}
```

---

## 📈 Performance Optimization Tips

### Pipeline Speed Improvements

1. **Use Maven Incremental Builds**
   ```groovy
   sh 'mvn clean install -T 4 -DskipTests'  // Parallel builds
   ```

2. **Cache Docker Layers**
   - Use multi-stage builds
   - Leverage BuildKit caching
   ```bash
   DOCKER_BUILDKIT=1 docker build --cache-from type=registry ...
   ```

3. **Parallel Test Execution**
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-surefire-plugin</artifactId>
       <configuration>
           <parallel>classes</parallel>
           <threadCount>4</threadCount>
       </configuration>
   </plugin>
   ```

---

## 🔗 Quick Links

- **Jenkins Dashboard:** `http://your-jenkins-url/`
- **GitHub Repository:** `https://github.com/julian-razif-figaro/bigdata`
- **AWS ECR Console:** `https://ap-southeast-1.console.aws.amazon.com/ecr/`
- **Application Health:** `http://prod-server:8087/actuator/health`

---

## 📞 Emergency Contacts

| Issue Type | Contact | Action |
|------------|---------|--------|
| Pipeline Failure | DevOps Team | Check Jenkins console logs |
| Deployment Failure | SRE Team | Trigger manual rollback |
| Application Down | On-Call Engineer | Check Docker service logs |
| Security Issue | Security Team | Rotate credentials immediately |

---

**Quick Reference Version:** 1.0.0  
**Last Updated:** January 9, 2026  
**For detailed setup instructions, see:** [CICD_SETUP_GUIDE.md](CICD_SETUP_GUIDE.md)
