pipeline {
    agent any
    
    options {
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 1, unit: 'HOURS')
    }
    
    environment {
        AWS_REGION = 'ap-southeast-1'
        ECR_REGISTRY = '451726692073.dkr.ecr.ap-southeast-1.amazonaws.com'
        IMAGE_NAME = 'kafka-to-pv-dynamo-service'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        MAVEN_OPTS = '-Xmx2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1'
    }
    
    stages {
        stage('SCM Checkout') {
            steps {
                script {
                    echo "Checking out code from GitHub repository..."
                }
                git branch: 'production',
                    credentialsId: 'julian-ssh-jenkins',
                    url: 'git@github.com:julian-razif-figaro/bigdata.git',
                    poll: false
            }
        }
        
        stage('Run Tests') {
            steps {
                script {
                    echo "Running unit tests with coverage..."
                }
                sh 'mvn test jacoco:report'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                    jacoco execPattern: '**/target/jacoco.exec'
                }
            }
        }
        
        stage('Build Maven') {
            steps {
                script {
                    echo "Building multi-module Maven project..."
                }
                sh 'mvn clean install -DskipTests'
            }
        }
        
        stage('Build Docker Image') {
            steps {
                script {
                    echo "Building Docker image and pushing to AWS ECR..."
                }
                withAWS(credentials: 'aws-credential', region: "${AWS_REGION}") {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                        docker-compose -f docker-compose.yml build
                        docker tag ${ECR_REGISTRY}/${IMAGE_NAME}:latest ${ECR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}
                        docker-compose -f docker-compose.yml push
                        docker push ${ECR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}
                        docker rmi -f \$(docker images -q ${ECR_REGISTRY}/${IMAGE_NAME} 2>/dev/null) || true
                    """
                }
            }
        }
        
        stage('Deploy with Ansible') {
            steps {
                script {
                    echo "Deploying to production servers via Ansible..."
                }
                ansiblePlaybook(
                    credentialsId: 'julian-private-key',
                    inventory: 'inventori.ini',
                    playbook: 'ansible-deploy.yml',
                    hostKeyChecking: false,
                    extras: "-e image_tag=${IMAGE_TAG}"
                )
            }
        }
        
        stage('Health Check') {
            steps {
                script {
                    echo "Performing post-deployment health checks..."
                    sleep(time: 30, unit: 'SECONDS')
                    sh '''
                        # Health check will be performed by Ansible playbook
                        echo "Health check completed via Ansible"
                    '''
                }
            }
        }
    }
    
    post {
        success {
            echo "✅ Pipeline completed successfully! Image: ${ECR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
            // Optional: Add Slack/Email notification
            // slackSend color: 'good', message: "Deployment successful: ${env.JOB_NAME} - ${env.BUILD_NUMBER}"
        }
        failure {
            echo "❌ Pipeline failed! Check logs for details."
            // Optional: Add Slack/Email notification
            // slackSend color: 'danger', message: "Deployment failed: ${env.JOB_NAME} - ${env.BUILD_NUMBER}"
        }
        always {
            cleanWs()
        }
    }
}
