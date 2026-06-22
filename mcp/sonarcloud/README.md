# SonarCloud MCP Server

MCP server for SonarCloud quality metrics integration.

## Setup

1. Install dependencies:
   ```bash
   npm install
   ```

2. Set environment variables:
   ```bash
   export SONAR_TOKEN=your_sonarcloud_token
   export SONAR_PROJECT_KEY=JPablo0505_medistock
   export SONAR_ORGANIZATION=jpablo0505
   ```

3. Start the server:
   ```bash
   npm start
   ```

## Available Tools

- `get_project_status` - Overall quality status and metrics
- `get_issues` - Filter issues by severity, type, status
- `get_coverage` - Code coverage details
- `get_security_hotspots` - Security vulnerabilities
- `get_code_smells` - Maintainability issues

## Integration with OpenCode

Add to `opencode.json`:

```json
{
  "mcp": {
    "sonarcloud": {
      "command": "node",
      "args": ["mcp/sonarcloud/index.js"],
      "env": {
        "SONAR_TOKEN": "${SONAR_TOKEN}",
        "SONAR_PROJECT_KEY": "JPablo0505_medistock",
        "SONAR_ORGANIZATION": "jpablo0505"
      }
    }
  }
}
```
