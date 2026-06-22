import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import axios from "axios";

// ─── Configuration ──────────────────────────────────────────────────────
const SONAR_TOKEN = process.env.SONAR_TOKEN;
const SONAR_HOST = process.env.SONAR_HOST_URL || "https://sonarcloud.io";
const PROJECT_KEY = process.env.SONAR_PROJECT_KEY || "JPablo0505_medistock";
const ORGANIZATION = process.env.SONAR_ORGANIZATION || "jpablo0505";

if (!SONAR_TOKEN) {
  console.error("SONAR_TOKEN environment variable is required");
  process.exit(1);
}

// ─── SonarCloud API Client ──────────────────────────────────────────────
const sonarApi = axios.create({
  baseURL: SONAR_HOST,
  auth: {
    username: SONAR_TOKEN,
    password: "",
  },
  params: {
    component: PROJECT_KEY,
    organization: ORGANIZATION,
  },
});

// ─── MCP Server ─────────────────────────────────────────────────────────
const server = new McpServer({
  name: "sonarcloud",
  version: "1.0.0",
});

// ─── Tool: Get Project Status ───────────────────────────────────────────
server.tool(
  "get_project_status",
  "Get overall SonarCloud project quality status and metrics",
  {},
  async () => {
    try {
      const [measuresRes, qualityGate] = await Promise.all([
        sonarApi.get("/api/measures/component", {
          params: {
            metricKeys:
              "bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density,ncloc,sqale_index,reliability_rating,security_rating",
          },
        }),
        sonarApi.get("/api/qualitygates/project_status"),
      ]);

      const measures = measuresRes.data.component.measures;
      const gate = qualityGate.data.projectStatus;

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                project: PROJECT_KEY,
                quality_gate: {
                  status: gate.status,
                  conditions: gate.conditions?.map((c) => ({
                    metric: c.metricKey,
                    status: c.status,
                    actual: c.actual?.value || c.actual,
                    threshold: c.errorThreshold,
                  })),
                },
                metrics: measures.reduce((acc, m) => {
                  acc[m.metric] = m.value;
                  return acc;
                }, {}),
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error fetching project status: ${error.message}`,
          },
        ],
        isError: true,
      };
    }
  }
);

// ─── Tool: Get Issues ───────────────────────────────────────────────────
server.tool(
  "get_issues",
  "Get SonarCloud issues for the project",
  {
    severity: {
      type: "string",
      description: "Filter by severity: BLOCKER, CRITICAL, MAJOR, MINOR, INFO",
    },
    type: {
      type: "string",
      description: "Filter by type: BUG, VULNERABILITY, CODE_SMELL",
    },
    status: {
      type: "string",
      description: "Filter by status: OPEN, CONFIRMED, RESOLVED, CLOSED",
    },
    limit: {
      type: "number",
      description: "Max issues to return (default: 20)",
    },
  },
  async (args) => {
    try {
      const params = {
        ps: args.limit || 20,
        resolved: "false",
      };

      if (args.severity) params.severities = args.severity;
      if (args.type) params.types = args.type;
      if (args.status) params.status = args.status;

      const response = await sonarApi.get("/api/issues/search", { params });
      const issues = response.data.issues;

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                total: response.data.total,
                issues: issues.map((i) => ({
                  key: i.key,
                  rule: i.rule,
                  severity: i.severity,
                  component: i.component?.split(":").pop(),
                  line: i.line,
                  message: i.message,
                  type: i.type,
                  status: i.status,
                })),
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error fetching issues: ${error.message}`,
          },
        ],
        isError: true,
      };
    }
  }
);

// ─── Tool: Get Coverage ─────────────────────────────────────────────────
server.tool(
  "get_coverage",
  "Get code coverage details for the project",
  {},
  async () => {
    try {
      const response = await sonarApi.get("/api/measures/component", {
        params: {
          metricKeys: "coverage,line_coverage,branch_coverage",
        },
      });

      const measures = response.data.component.measures;

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                project: PROJECT_KEY,
                coverage: measures.reduce((acc, m) => {
                  acc[m.metric] = m.value;
                  return acc;
                }, {}),
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error fetching coverage: ${error.message}`,
          },
        ],
        isError: true,
      };
    }
  }
);

// ─── Tool: Get Security Hotspots ────────────────────────────────────────
server.tool(
  "get_security_hotspots",
  "Get security hotspots for the project",
  {
    status: {
      type: "string",
      description: "Filter by status: TO_REVIEW, IN_REVIEW, ACCEPTED, FIXED",
    },
  },
  async (args) => {
    try {
      const params = {};
      if (args.status) params.status = args.status;

      const response = await sonarApi.get(
        "/api/hotspots/search",
        { params }
      );
      const hotspots = response.data.hotspots;

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                total: response.data.paging?.total || hotspots.length,
                hotspots: hotspots.map((h) => ({
                  key: h.key,
                  message: h.message,
                  component: h.component?.split(":").pop(),
                  line: h.line,
                  status: h.status,
                  vulnerabilityProbability:
                    h.vulnerabilityProbability,
                })),
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error fetching security hotspots: ${error.message}`,
          },
        ],
        isError: true,
      };
    }
  }
);

// ─── Tool: Get Code Smells ──────────────────────────────────────────────
server.tool(
  "get_code_smells",
  "Get code smells (maintainability issues) for the project",
  {
    limit: {
      type: "number",
      description: "Max code smells to return (default: 20)",
    },
  },
  async (args) => {
    try {
      const response = await sonarApi.get("/api/issues/search", {
        params: {
          types: "CODE_SMELL",
          ps: args.limit || 20,
          resolved: "false",
        },
      });

      const issues = response.data.issues;

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                total: response.data.total,
                code_smells: issues.map((i) => ({
                  key: i.key,
                  rule: i.rule,
                  severity: i.severity,
                  component: i.component?.split(":").pop(),
                  line: i.line,
                  message: i.message,
                })),
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error fetching code smells: ${error.message}`,
          },
        ],
        isError: true,
      };
    }
  }
);

// ─── Start Server ───────────────────────────────────────────────────────
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("SonarCloud MCP server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});
