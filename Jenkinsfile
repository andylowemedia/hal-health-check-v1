#!groovy
node {
//     stage ('Building Environment') {
//         try {
//             checkout scm
//             sh "sed -i \"s/#{TAG_NAME}#/${env.TAG_NAME}/\" docker-compose.jenkins.yml"
//             sh "sed -i \"s/#{BUILD_NAME}#/${currentBuild.number}/\" docker-compose.jenkins.yml"
//             sh "docker network create hal-scrape-${env.TAG_NAME}-build-${currentBuild.number}_default"
//             sh 'docker-compose -f docker-compose.jenkins.yml build --no-cache && docker-compose -f docker-compose.jenkins.yml up -d'
//         } catch (err) {
//             sh 'docker-compose -f docker-compose.jenkins.yml down -v'
//             sh "docker network rm hal-scrape-${env.TAG_NAME}-build-${currentBuild.number}_default"
//             sh "sudo rm -rf *"
//             sh "sudo rm -rf .git .gitignore"
//             sh "docker system prune -f"
//             throw err
//         }
//     }
//     stage ('Docker Cleanup') {
//         sh 'docker-compose -f docker-compose.jenkins.yml down -v'
//         sh "docker network rm hal-sources-${env.TAG_NAME}-build-${currentBuild.number}_default"
//         sh "sudo rm -rf *"
//         sh "sudo rm -rf .git .gitignore"
//         sh "docker system prune -f"
//     }
    stage ('Building & Push Docker Image') {
        checkout scm
        def tag = sh(returnStdout: true, script: "git tag --contains | head -1").trim()

        docker.build("low-emedia/hal-health-check-v1:latest", "-f cli/Dockerfile cli/.")
        docker.withRegistry('https://540688370389.dkr.ecr.eu-west-1.amazonaws.com', 'ecr:eu-west-1:aws-lowemedia') {
            docker.image("low-emedia/hal-health-check-v1").push(tag)
        }
        docker.withRegistry('https://540688370389.dkr.ecr.eu-west-1.amazonaws.com', 'ecr:eu-west-1:aws-lowemedia') {
            docker.image("low-emedia/hal-health-check-v1").push("latest")
        }

        sh "docker run --name hal-health-check-app-${env.TAG_NAME}-${currentBuild.number} -d low-emedia/hal-health-check-v1"
        sh "docker cp hal-health-check-app-${env.TAG_NAME}-${currentBuild.number}:/app/bin ./bin"
        sh "docker cp hal-health-check-app-${env.TAG_NAME}-${currentBuild.number}:/app/lib ./lib"
        sh "docker stop hal-health-check-app-${env.TAG_NAME}-${currentBuild.number}"
        sh "zip -r app.zip bin lib"

        withAWS(region:"eu-west-1", credentials: "aws-lowemedia"){
            s3Upload(file: "app.zip", bucket:"low-emedia-apps", path:"hal-health-check-v1/${tag}/")
        }
        
        sh "docker system prune -f"
    }
}