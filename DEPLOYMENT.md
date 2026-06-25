# Deployment Configuration

This project uses GitHub Actions for automated deployment to AWS S3 and CloudFront.

## GitHub Actions Secrets

To enable the deployment workflow, the following secrets must be configured in the GitHub repository (`Settings > Secrets and variables > Actions`):

| Secret Name | Description |
| ----------- | ----------- |
| `AWS_ACCESS_KEY_ID` | AWS Access Key ID with permissions for S3 and CloudFront. |
| `AWS_SECRET_ACCESS_KEY` | AWS Secret Access Key. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | The full JSON content of the Firebase Service Account key for Firestore ratings. |

## Workflow Details

The workflow is located at `.github/workflows/deploy.yml`. It:
1. Triggers on pushes to the `main` branch.
2. Sets up Java 26. (Note: Ensure that the GitHub runner or the `actions/setup-java` action supports this version).
3. Configures AWS credentials.
4. Runs `de.maulmann.SiteBuilderPipeline` via Maven with preview features enabled.
