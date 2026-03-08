pipeline {
    agent any

    parameters {
        string(name: 'PR_NUMBER',    defaultValue: '1',               description: 'Pull Request Number')
        string(name: 'PR_TITLE',     defaultValue: 'Add test coverage', description: 'Pull Request Title')
        string(name: 'MERGE_SHA',    defaultValue: 'main',            description: 'Merge Commit SHA / image tag')
        string(name: 'REQUIREMENTS', defaultValue: '',                description: 'Requirements text (optional)')
    }

    environment {
        PROJECT_DIR       = 'C:\\Users\\madda\\customer-service'
        PYTHON            = 'C:\\Users\\madda\\AppData\\Local\\Python\\bin\\python.exe'
        MVN_PATH          = 'C:\\Users\\madda\\.m2\\wrapper\\dists\\apache-maven-3.8.7-bin\\1ktonn2lleg549uah6ngl1r74r\\apache-maven-3.8.7\\bin\\mvn.cmd'
        EMAIL_OWNER       = 'maddalaharika@gmail.com'
        SMTP_HOST         = 'smtp.gmail.com'
        SMTP_PORT         = '587'
        ANTHROPIC_API_KEY = credentials('ANTHROPIC_API_KEY')
        GH_TOKEN          = credentials('GH_TOKEN')
        SMTP_USER         = credentials('SMTP_USER')
        SMTP_PASSWORD     = credentials('SMTP_PASSWORD')
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        // ── PHASE 1 — ANALYSIS ─────────────────────────────────────────────
        stage('Phase 1: Analysis') {
            steps {
                echo "=== PHASE 1: AI Requirements Analysis ==="
                dir("${PROJECT_DIR}") {
                    bat "\"%PYTHON%\" jenkins_runner.py analysis --build-number %BUILD_NUMBER% --pr-title \"%PR_TITLE%\" --requirements \"%REQUIREMENTS%\""
                }
            }
        }

        // ── PHASE 2 — BUILD ────────────────────────────────────────────────
        stage('Phase 2: Build') {
            steps {
                echo "=== PHASE 2: Maven Compile ==="
                dir("${PROJECT_DIR}") {
                    bat "\"%PYTHON%\" jenkins_runner.py build --build-number %BUILD_NUMBER%"
                }
            }
        }

        // ── PHASE 3 — TEST + COVERAGE ──────────────────────────────────────
        stage('Phase 3: Test + Coverage') {
            steps {
                echo "=== PHASE 3: Maven Verify (Tests + JaCoCo >= 80%) ==="
                dir("${PROJECT_DIR}") {
                    bat "\"%PYTHON%\" jenkins_runner.py test --build-number %BUILD_NUMBER%"
                }
            }
            post {
                always {
                    dir("${PROJECT_DIR}") {
                        junit allowEmptyResults: true,
                              testResults: 'target/surefire-reports/*.xml'
                    }
                }
            }
        }

        // ── PHASE 4 — SECURITY & QUALITY SCAN ────────────────────────────
        stage('Phase 4: Security Scan') {
            steps {
                echo "=== PHASE 4: SpotBugs + OWASP Dependency Check + AI Review ==="
                dir("${PROJECT_DIR}") {
                    script {
                        def out = bat(returnStdout: true,
                            script: "\"%PYTHON%\" jenkins_runner.py security --build-number %BUILD_NUMBER%"
                        ).trim()
                        echo out
                        if (out.contains('SECURITY_FAILED')) {
                            error("SECURITY SCAN FAILED — Critical CVEs detected. Deployment blocked.")
                        }
                    }
                }
            }
        }

        // ── PHASE 5 — ICA ──────────────────────────────────────────────────
        stage('Phase 5: ICA') {
            steps {
                echo "=== PHASE 5: AI Risk Scoring + Instant Change Authorization ==="
                dir("${PROJECT_DIR}") {
                    script {
                        def out = bat(returnStdout: true,
                            script: "\"%PYTHON%\" jenkins_runner.py ica --build-number %BUILD_NUMBER% --pr-title \"%PR_TITLE%\""
                        ).trim()
                        echo out
                        if (out.contains('ICA_BLOCKED')) {
                            error("ICA BLOCKED — Change risk too high. Deployment halted.")
                        }
                    }
                }
            }
        }

        // ── PHASE 6 — GAIA QA DEPLOYMENT ──────────────────────────────────
        stage('Phase 6: Gaia QA Deploy') {
            steps {
                echo "=== PHASE 6: Deploy to Gaia QA Cluster ==="
                dir("${PROJECT_DIR}") {
                    script {
                        def out = bat(returnStdout: true,
                            script: "\"%PYTHON%\" jenkins_runner.py deploy --build-number %BUILD_NUMBER% --merge-sha \"%MERGE_SHA%\""
                        ).trim()
                        echo out
                        def depIdMatch     = out =~ /DEP_ID:(.+)/
                        def depStatusMatch = out =~ /DEP_STATUS:(.+)/
                        env.DEPLOYMENT_ID  = depIdMatch     ? depIdMatch[0][1].trim()     : 'unknown'
                        env.DEPLOY_STATUS  = depStatusMatch ? depStatusMatch[0][1].trim() : 'unknown'
                        echo "Deployment ID: ${env.DEPLOYMENT_ID} | Status: ${env.DEPLOY_STATUS}"
                    }
                }
            }
        }

        // ── PHASE 6b — QA GATE ─────────────────────────────────────────────
        stage('Phase 7: QA Gate') {
            steps {
                echo "=== PHASE 6b: QA Agent / Rollback ==="
                dir("${PROJECT_DIR}") {
                    bat "\"%PYTHON%\" jenkins_runner.py qa-gate --build-number %BUILD_NUMBER% --pr-number \"%PR_NUMBER%\" --pr-title \"%PR_TITLE%\" --merge-sha \"%MERGE_SHA%\" --deployment-id \"%DEPLOYMENT_ID%\" --deploy-status \"%DEPLOY_STATUS%\""
                }
            }
        }

        // ── PHASE 8 — EVIDENCE PACK ────────────────────────────────────────
        stage('Phase 8: Evidence Pack') {
            steps {
                echo "=== PHASE 8: Generate HTML + JSON Evidence Pack ==="
                dir("${PROJECT_DIR}") {
                    bat "\"%PYTHON%\" jenkins_runner.py evidence --build-number %BUILD_NUMBER%"
                }
            }
            post {
                always {
                    dir("${PROJECT_DIR}") {
                        archiveArtifacts artifacts: "evidence/jenkins-run-${BUILD_NUMBER}/**",
                                         allowEmptyArchive: true
                    }
                }
            }
        }

        // ── PHASE 9 — NOTIFY ───────────────────────────────────────────────
        stage('Phase 9: Notify') {
            steps {
                echo "=== PHASE 9: Send SDLC Outcome Email ==="
                dir("${PROJECT_DIR}") {
                    bat "\"%PYTHON%\" jenkins_runner.py notify --build-number %BUILD_NUMBER% --pr-number \"%PR_NUMBER%\" --pr-title \"%PR_TITLE%\" --ica-decision AUTO_APPROVED --deploy-status \"%DEPLOY_STATUS%\""
                }
            }
        }
    }

    post {
        success {
            echo "SDLC Pipeline COMPLETED successfully for PR #${params.PR_NUMBER}"
        }
        failure {
            echo "SDLC Pipeline FAILED — sending failure notification..."
            dir("${PROJECT_DIR}") {
                bat "\"%PYTHON%\" jenkins_runner.py notify --build-number %BUILD_NUMBER% --pr-number \"%PR_NUMBER%\" --pr-title \"%PR_TITLE%\" --ica-decision UNKNOWN --deploy-status pipeline_failed || echo Notification also failed."
            }
        }
    }
}
