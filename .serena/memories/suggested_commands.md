# Suggested Commands

## Code Editing & Management
- Windows environment: Use standard PowerShell or CMD utilities.
- When working with Android/Kotlin code, standard Gradle commands will apply:
  - `./gradlew build` (to build the project)
  - `./gradlew test` (to run tests)
  
## LiteRT-LM (CLI)
For prototyping and managing LLM models, the LiteRT-LM CLI is useful:
- Install: `uv tool install litert-lm`
- Run Model: `litert-lm run --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm --prompt="Hello"`
- Import: `litert-lm import ./model.litertlm my-model`

## Node.js / saizeriya.js
- Install dependencies: `npm install`
- Run scripts: `npm run dev` or `node index.js` (once implemented)