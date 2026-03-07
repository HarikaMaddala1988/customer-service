pipeline {
    agent any

    parameters {
        string(name: 'PR_NUMBER',    defaultValue: '1',               description: 'Pull Request Number')
        string(name: 'PR_TITLE',     defaultValue: 'Add test coverage', description: 'Pull Request Title')
        string(name: 'MERGE_SHA',    defaultValue: 'main',            description: 'Merge Commit SHA / image tag')
        string(name: 'REQUIREMENTS', defaultValue: '',                description: 'Requirements text (optional)')
    }

    environment {
        PROJECT_DIR  = 'C:\\Users\\madda\\customer-service'
        PYTHON       = 'python'
        MVN          = 'mvn'
        EMAIL_OWNER  = 'maddalaharika@gmail.com'
        SMTP_HOST    = 'smtp.gmail.com'
        SMTP_PORT    = '587'
        // Secrets injected from Jenkins credentials store
        ANTHROPIC_API_KEY = credentials('ANTHROPIC_API_KEY')
        GH_TOKEN          = credentials('GH_TOKEN')
        SMTP_USER_VAR     = credentials('SMTP_USER')
        SMTP_PASSWORD_VAR = credentials('SMTP_PASSWORD')
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
                    script {
                        def req = params.REQUIREMENTS?.trim() ? params.REQUIREMENTS : params.PR_TITLE
                        writeFile file: 'jenkins_analysis_input.txt', text: req
                    }
                    bat """
                        set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                        %PYTHON% -c "
import os, sys
sys.path.insert(0, '.')
from sdlc_orchestrator import analyze_requirements, EvidencePack
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
result = analyze_requirements(open('jenkins_analysis_input.txt').read(), ev)
print('Analysis complete.')
print(result.get('analysis','')[:500])
"
                    """
                }
            }
        }

        // ── PHASE 2 — BUILD ────────────────────────────────────────────────
        stage('Phase 2: Build') {
            steps {
                echo "=== PHASE 2: Maven Compile ==="
                dir("${PROJECT_DIR}") {
                    bat "%MVN% compile -q"
                }
            }
            post {
                failure { echo "BUILD FAILED — pipeline will stop after this stage." }
            }
        }

        // ── PHASE 3 — TEST + COVERAGE ──────────────────────────────────────
        stage('Phase 3: Test + Coverage') {
            steps {
                echo "=== PHASE 3: Maven Verify (Tests + JaCoCo >= 80%) ==="
                dir("${PROJECT_DIR}") {
                    bat "%MVN% verify"
                }
            }
            post {
                always {
                    dir("${PROJECT_DIR}") {
                        junit allowEmptyResults: true,
                              testResults: 'target/surefire-reports/*.xml'
                    }
                }
                failure { echo "TESTS OR COVERAGE FAILED — pipeline will stop." }
            }
        }

        // ── PHASE 4 — ICA (Instant Change Authorization) ───────────────────
        stage('Phase 4: ICA') {
            steps {
                echo "=== PHASE 4: AI Risk Scoring + Instant Change Authorization ==="
                dir("${PROJECT_DIR}") {
                    script {
                        def output = bat(returnStdout: true, script: """
                            set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                            %PYTHON% -c "
import os, sys, json
sys.path.insert(0, '.')
from sdlc_orchestrator import assess_change_risk, instant_change_authorization, EvidencePack
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
risk = assess_change_risk(
    pr_title='${params.PR_TITLE}',
    test_passed=True,
    coverage_summary='JaCoCo >= 80% passed',
    files_changed=5,
    evidence=ev
)
print('RISK_SCORE:' + str(risk['risk_score']))
print('RISK_CATEGORY:' + risk['risk_category'])
ica = instant_change_authorization(
    risk_score=risk['risk_score'],
    risk_category=risk['risk_category'],
    recommendation=risk['recommendation'],
    risk_factors=risk['risk_factors'],
    pr_title='${params.PR_TITLE}',
    evidence=ev
)
print('ICA_DECISION:' + ica['ica_decision'])
print('ICA_AUTHORIZED:' + str(ica['authorized']))
if not ica['authorized']:
    sys.exit(1)
"
                        """).trim()

                        echo "ICA Output:\n${output}"
                        if (output.contains('ICA_AUTHORIZED:False')) {
                            error("ICA BLOCKED — Change risk too high. Deployment halted.")
                        }
                    }
                }
            }
        }

        // ── PHASE 5 — JULES CI/CD PIPELINE ────────────────────────────────
        stage('Phase 5: Jules Pipeline') {
            steps {
                echo "=== PHASE 5: Trigger Jules CI/CD Pipeline ==="
                dir("${PROJECT_DIR}") {
                    bat """
                        set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                        %PYTHON% -c "
import os, sys, json, time
sys.path.insert(0, '.')
from sdlc_orchestrator import trigger_jules_pipeline, get_jules_pipeline_status, EvidencePack
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
trig = trigger_jules_pipeline('customer-service-deploy', '${params.MERGE_SHA}', ev)
print('Triggered:', json.dumps(trig))
run_id = trig.get('pipeline_run_id')
status = {}
for i in range(20):
    status = get_jules_pipeline_status(run_id)
    print(f'Poll {i+1}: {status.get(chr(115)+chr(116)+chr(97)+chr(116)+chr(117)+chr(115))}')
    if status.get('finished'):
        break
    time.sleep(5)
print('PIPELINE_STATUS:' + str(status.get('success', False)))
if not status.get('success'):
    sys.exit(1)
"
                    """
                }
            }
        }

        // ── PHASE 6 — GAIA QA DEPLOYMENT ──────────────────────────────────
        stage('Phase 6: Gaia QA Deploy') {
            steps {
                echo "=== PHASE 6: Deploy to Gaia QA Cluster ==="
                dir("${PROJECT_DIR}") {
                    script {
                        def output = bat(returnStdout: true, script: """
                            set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                            %PYTHON% -c "
import os, sys, json, time
sys.path.insert(0, '.')
from sdlc_orchestrator import deploy_to_gaia, get_gaia_deployment_status, EvidencePack
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
dep = deploy_to_gaia('${params.MERGE_SHA}', ev)
print('Deployed:', json.dumps(dep))
dep_id = dep.get('deployment_id', '')
status = {}
for i in range(20):
    status = get_gaia_deployment_status(dep_id)
    print(f'Poll {i+1}: {status.get(chr(115)+chr(116)+chr(97)+chr(116)+chr(117)+chr(115))}')
    if status.get('finished'):
        break
    time.sleep(5)
print('DEP_ID:' + str(dep_id))
print('DEP_STATUS:' + str(status.get('status','unknown')))
"
                        """).trim()

                        echo "Deploy Output:\n${output}"
                        def depIdMatch    = output =~ /DEP_ID:(.+)/
                        def depStatusMatch = output =~ /DEP_STATUS:(.+)/
                        env.DEPLOYMENT_ID  = depIdMatch    ? depIdMatch[0][1].trim()    : 'unknown'
                        env.DEPLOY_STATUS  = depStatusMatch ? depStatusMatch[0][1].trim() : 'unknown'
                        echo "Deployment ID: ${env.DEPLOYMENT_ID} | Status: ${env.DEPLOY_STATUS}"
                    }
                }
            }
        }

        // ── PHASE 6b — QA FUNCTIONAL TESTING GATE ─────────────────────────
        stage('Phase 6b: QA Gate') {
            steps {
                echo "=== PHASE 6b: QA Agent / Rollback ==="
                dir("${PROJECT_DIR}") {
                    script {
                        if (env.DEPLOY_STATUS == 'healthy') {
                            bat """
                                set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                                %PYTHON% -c "
import os, sys, json
sys.path.insert(0, '.')
from sdlc_orchestrator import trigger_qa_agent, EvidencePack
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
result = trigger_qa_agent(
    pr_number=int('${params.PR_NUMBER}'),
    pr_title='${params.PR_TITLE}',
    merge_sha='${params.MERGE_SHA}',
    deployment_id='${env.DEPLOYMENT_ID}',
    evidence=ev
)
print('QA Agent:', json.dumps(result))
"
                            """
                        } else {
                            bat """
                                set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                                %PYTHON% -c "
import os, sys, json
sys.path.insert(0, '.')
from sdlc_orchestrator import rollback_gaia_deployment, EvidencePack
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
result = rollback_gaia_deployment('${env.DEPLOYMENT_ID}', 'QA deployment degraded', ev)
print('Rollback:', json.dumps(result))
"
                            """
                            error("QA Deployment failed — rolled back. Check Gaia dashboard.")
                        }
                    }
                }
            }
        }

        // ── PHASE 8 — EVIDENCE PACK ────────────────────────────────────────
        stage('Phase 8: Evidence Pack') {
            steps {
                echo "=== PHASE 8: Generate HTML + JSON Evidence Pack ==="
                dir("${PROJECT_DIR}") {
                    bat """
                        set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                        %PYTHON% -c "
import os, sys
sys.path.insert(0, '.')
from sdlc_orchestrator import EvidencePack
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
html = ev.generate_html_report()
manifest = ev.generate_json_manifest()
print('HTML Report :', html)
print('JSON Manifest:', manifest)
"
                    """
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
                    bat """
                        set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                        set SMTP_USER=%SMTP_USER_VAR%
                        set SMTP_PASSWORD=%SMTP_PASSWORD_VAR%
                        %PYTHON% -c "
import os, sys, json
sys.path.insert(0, '.')
from sdlc_orchestrator import send_sdlc_notification, EvidencePack
from pathlib import Path
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
html_path = str(Path('evidence') / 'jenkins-run-%BUILD_NUMBER%' / 'evidence_report.html')
ev_dir    = str(Path('evidence') / 'jenkins-run-%BUILD_NUMBER%')
result = send_sdlc_notification(
    pr_number=int('${params.PR_NUMBER}'),
    pr_title='${params.PR_TITLE}',
    ica_decision='AUTO_APPROVED',
    deploy_status='${env.DEPLOY_STATUS ?: 'healthy'}',
    html_report_path=html_path,
    evidence_dir=ev_dir,
    evidence=ev
)
print('Notification result:', json.dumps(result))
"
                    """
                }
            }
        }
    }

    // ── POST — failure catch-all ───────────────────────────────────────────
    post {
        success {
            echo "SDLC Pipeline COMPLETED for PR #${params.PR_NUMBER} — ${params.PR_TITLE}"
        }
        failure {
            echo "SDLC Pipeline FAILED — sending failure notification..."
            dir("${PROJECT_DIR}") {
                bat """
                    set ANTHROPIC_API_KEY=%ANTHROPIC_API_KEY%
                    set SMTP_USER=%SMTP_USER_VAR%
                    set SMTP_PASSWORD=%SMTP_PASSWORD_VAR%
                    %PYTHON% -c "
import os, sys
sys.path.insert(0, '.')
from sdlc_orchestrator import send_sdlc_notification, EvidencePack
from pathlib import Path
ev = EvidencePack('jenkins-run-%BUILD_NUMBER%')
html_path = str(Path('evidence') / 'jenkins-run-%BUILD_NUMBER%' / 'evidence_report.html')
ev_dir    = str(Path('evidence') / 'jenkins-run-%BUILD_NUMBER%')
send_sdlc_notification(
    pr_number=int('${params.PR_NUMBER}'),
    pr_title='${params.PR_TITLE}',
    ica_decision='UNKNOWN',
    deploy_status='pipeline_failed',
    html_report_path=html_path,
    evidence_dir=ev_dir,
    evidence=ev
)
print('Failure notification sent.')
" || echo "Notification also failed."
                """
            }
        }
    }
}
