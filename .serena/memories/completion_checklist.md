# Task Completion Checklist

When completing a feature or task, ensure the following steps are performed:

1. **Build Verification**: Run `./gradlew build` (for Kotlin/Android) or `npm run build` (for JS/TS) to ensure no compilation errors.
2. **Linting/Formatting**: Run formatters to maintain code style consistency.
3. **Documentation**: Update `README.md` if the architectural design, setup steps, or API dependencies change.
4. **Testing**: Run available unit or integration tests (e.g., `./gradlew test`). Ensure that ML inference pipelines mock the model if running on CI/CD or non-NPU environments.