# Grafana Cloud dashboard and alerts for one environment (task 26, decision 121).
#
# The queries use Grafana's span metrics (traces_spanmetrics_*), which Grafana
# Cloud derives from the traces we send, rather than the API's own
# http_server_request_duration_seconds. Each Lambda instance has its own
# cumulative series (decision 118) that starts at its first request without a
# zero before it, so rate() and increase() over the API's metrics miss that
# first request. A one-user app's instances serve a few requests each, so most
# requests would be missed. Span metrics are kept per service and version, and
# a new series starts at 0.

locals {
  # Same data source uids on every Grafana Cloud stack.
  prom   = "grafanacloud-prom"
  logs   = "grafanacloud-logs"
  traces = "grafanacloud-traces"

  env_label = "deployment_environment_name=\"${var.env}\""
  api       = "service=\"dinogym-api\", ${local.env_label}"
  reminders = "service=\"dinogym-reminders\", ${local.env_label}"
  server    = "span_kind=\"SPAN_KIND_SERVER\""
  error     = "status_code=\"STATUS_CODE_ERROR\""

  traceql_errors = "{ resource.deployment.environment.name = \"${var.env}\" && resource.service.name =~ \"dinogym-api|dinogym-reminders\" && (status = error || (kind = server && duration > 1s)) }"
  logql_all      = "{service_name=~\"dinogym-api|dinogym-reminders\", ${local.env_label}}"
}

# The folder is made once by hand, not here (infra/README.md, decision 121).
# A folder created through the API carries only the permissions it is created
# with, so the Editor service account could create it but not read it back.
# The owner grants that account Edit on the folder instead.
data "grafana_folder" "this" {
  uid = "dinogym-${var.env}"
}

resource "grafana_dashboard" "this" {
  folder    = data.grafana_folder.this.uid
  overwrite = true
  config_json = jsonencode({
    uid           = "dinogym-${var.env}"
    title         = "DinoGym ${var.env}"
    tags          = ["dinogym", var.env, "terraform"]
    timezone      = "browser"
    schemaVersion = 39
    refresh       = "1m"
    time          = { from = "now-24h", to = "now" }
    editable      = false
    description   = "Managed by Terraform (infra/modules/grafana). Edits in the UI are overwritten on the next deploy."
    links = [
      {
        title       = "Traces: errors and slow requests"
        type        = "link"
        targetBlank = true
        url = "/explore?schemaVersion=1&orgId=1&panes=${urlencode(jsonencode({
          a = {
            datasource = local.traces
            queries    = [{ refId = "A", datasource = { type = "tempo", uid = local.traces }, queryType = "traceql", query = local.traceql_errors, limit = 50 }]
            range      = { from = "now-24h", to = "now" }
          }
        }))}"
      },
      {
        title       = "Logs"
        type        = "link"
        targetBlank = true
        url = "/explore?schemaVersion=1&orgId=1&panes=${urlencode(jsonencode({
          a = {
            datasource = local.logs
            queries    = [{ refId = "A", datasource = { type = "loki", uid = local.logs }, expr = local.logql_all }]
            range      = { from = "now-24h", to = "now" }
          }
        }))}"
      },
    ]
    panels = [
      {
        id         = 1, type = "timeseries", title = "Requests per minute, by route"
        gridPos    = { x = 0, y = 0, w = 12, h = 8 }
        datasource = { type = "prometheus", uid = local.prom }
        targets = [{
          refId        = "A"
          expr         = "sum by (span_name) (rate(traces_spanmetrics_calls_total{${local.api}, ${local.server}}[$__rate_interval])) * 60"
          legendFormat = "{{span_name}}"
        }]
        fieldConfig = { defaults = { unit = "reqpm", min = 0 }, overrides = [] }
      },
      {
        id          = 2, type = "timeseries", title = "5xx per minute, by route"
        description = "Server spans with status error: otelhttp sets it for 5xx responses only, not 4xx."
        gridPos     = { x = 12, y = 0, w = 12, h = 8 }
        datasource  = { type = "prometheus", uid = local.prom }
        targets = [{
          refId        = "A"
          expr         = "sum by (span_name) (rate(traces_spanmetrics_calls_total{${local.api}, ${local.server}, ${local.error}}[$__rate_interval])) * 60"
          legendFormat = "{{span_name}}"
        }]
        fieldConfig = { defaults = { unit = "reqpm", min = 0, color = { mode = "fixed", fixedColor = "red" } }, overrides = [] }
      },
      {
        id          = 3, type = "timeseries", title = "p50 latency, by route"
        description = "Handler time, as the server span measures it. Excludes Lambda's cold start (init) and API Gateway."
        gridPos     = { x = 0, y = 8, w = 12, h = 8 }
        datasource  = { type = "prometheus", uid = local.prom }
        targets = [{
          refId        = "A"
          expr         = "histogram_quantile(0.5, sum by (span_name) (rate(traces_spanmetrics_latency{${local.api}, ${local.server}}[$__rate_interval])))"
          legendFormat = "{{span_name}}"
        }]
        fieldConfig = { defaults = { unit = "s", min = 0 }, overrides = [] }
      },
      {
        id          = 4, type = "timeseries", title = "p95 latency, by route"
        description = "Handler time, as the server span measures it. Excludes Lambda's cold start (init) and API Gateway."
        gridPos     = { x = 12, y = 8, w = 12, h = 8 }
        datasource  = { type = "prometheus", uid = local.prom }
        targets = [{
          refId        = "A"
          expr         = "histogram_quantile(0.95, sum by (span_name) (rate(traces_spanmetrics_latency{${local.api}, ${local.server}}[$__rate_interval])))"
          legendFormat = "{{span_name}}"
        }]
        fieldConfig = { defaults = { unit = "s", min = 0 }, overrides = [] }
      },
      {
        id          = 5, type = "stat", title = "API cold starts"
        description = "Distinct API instances that sent metrics in the time range. Each instance id is made at process start (decision 118), so one id is one cold start."
        gridPos     = { x = 0, y = 16, w = 6, h = 8 }
        datasource  = { type = "prometheus", uid = local.prom }
        targets = [{
          refId   = "A"
          expr    = "count(count by (instance) (count_over_time(target_info{job=\"dinogym-api\", ${local.env_label}}[$__range])))"
          instant = true
        }]
        options     = { reduceOptions = { calcs = ["lastNotNull"], fields = "", values = false }, colorMode = "none", graphMode = "none" }
        fieldConfig = { defaults = { unit = "none", noValue = "0" }, overrides = [] }
      },
      {
        id         = 6, type = "timeseries", title = "Reminders sent and failed per hour"
        gridPos    = { x = 6, y = 16, w = 18, h = 8 }
        datasource = { type = "prometheus", uid = local.prom }
        targets = [
          {
            refId        = "A"
            expr         = "sum(increase(traces_spanmetrics_calls_total{${local.reminders}, ${local.server}}[1h]))"
            legendFormat = "fired"
            interval     = "1h"
          },
          {
            refId        = "B"
            expr         = "sum(increase(traces_spanmetrics_calls_total{${local.reminders}, ${local.server}, ${local.error}}[1h]))"
            legendFormat = "failed"
            interval     = "1h"
          },
        ]
        fieldConfig = {
          defaults  = { unit = "none", min = 0, custom = { drawStyle = "bars", fillOpacity = 60 } }
          overrides = [{ matcher = { id = "byName", options = "failed" }, properties = [{ id = "color", value = { mode = "fixed", fixedColor = "red" } }] }]
        }
      },
      {
        id          = 7, type = "table", title = "Errors and slow requests (traces)"
        description = "Spans with status error, or server spans over 1 s. Select a trace id to open it."
        gridPos     = { x = 0, y = 24, w = 24, h = 9 }
        datasource  = { type = "tempo", uid = local.traces }
        targets = [{
          refId     = "A"
          queryType = "traceql"
          query     = local.traceql_errors
          limit     = 20
          tableType = "traces"
        }]
      },
      {
        id          = 8, type = "logs", title = "Logs"
        description = var.env == "prod" ? "Prod sends WARN and above." : "Staging sends DEBUG and above."
        gridPos     = { x = 0, y = 33, w = 24, h = 10 }
        datasource  = { type = "loki", uid = local.logs }
        targets     = [{ refId = "A", expr = local.logql_all }]
        options     = { showTime = true, wrapLogMessage = true, sortOrder = "Descending", enableLogDetails = true }
      },
    ]
  })
}

resource "grafana_contact_point" "email" {
  name = "dinogym-${var.env}-email"
  email {
    addresses               = [var.alert_email]
    single_email            = true
    disable_resolve_message = false
  }
}

# Each rule names the contact point itself (simplified routing), so neither
# environment owns the stack-wide notification policy tree.
resource "grafana_rule_group" "this" {
  name             = "dinogym-${var.env}"
  folder_uid       = data.grafana_folder.this.uid
  interval_seconds = 60

  dynamic "rule" {
    for_each = {
      api_5xx = {
        title   = "API 5xx responses"
        expr    = "sum(increase(traces_spanmetrics_calls_total{${local.api}, ${local.server}, ${local.error}}[15m]))"
        window  = 900
        above   = 0.5 # increase() extrapolates; any real error gives about 1
        for     = "0s"
        summary = "The ${var.env} API answered 5xx in the last 15 minutes ({{ $values.B.Value | printf \"%.0f\" }})."
        panel   = 2
      }
      api_p95 = {
        title   = "API p95 latency over 2 s"
        expr    = "histogram_quantile(0.95, sum(rate(traces_spanmetrics_latency{${local.api}, ${local.server}}[30m])))"
        window  = 1800
        above   = 2
        for     = "5m"
        summary = "The ${var.env} API's p95 handler time over 30 minutes is {{ $values.B.Value | printf \"%.2f\" }} s."
        panel   = 4
      }
      reminder_errors = {
        title   = "Reminder function failures"
        expr    = "sum(increase(traces_spanmetrics_calls_total{${local.reminders}, ${local.server}, ${local.error}}[1h]))"
        window  = 3600
        above   = 0.5
        for     = "0s"
        summary = "A ${var.env} reminder failed to reach any phone in the last hour; Scheduler retries it."
        panel   = 6
      }
    }

    content {
      name      = rule.value.title
      condition = "C"
      for       = rule.value.for
      is_paused = var.alerts_paused
      # No data means no matching spans: no traffic, or no errors.
      no_data_state  = "OK"
      exec_err_state = "Error"
      labels         = { app = "dinogym", env = var.env }
      annotations = {
        summary          = rule.value.summary
        __dashboardUid__ = grafana_dashboard.this.uid
        __panelId__      = tostring(rule.value.panel)
      }
      notification_settings {
        contact_point = grafana_contact_point.email.name
      }

      data {
        ref_id         = "A"
        datasource_uid = local.prom
        relative_time_range {
          from = rule.value.window
          to   = 0
        }
        model = jsonencode({ refId = "A", expr = rule.value.expr, instant = true })
      }
      data {
        ref_id         = "B"
        datasource_uid = "__expr__"
        relative_time_range {
          from = 0
          to   = 0
        }
        model = jsonencode({ refId = "B", type = "reduce", expression = "A", reducer = "last", settings = { mode = "dropNN" } })
      }
      data {
        ref_id         = "C"
        datasource_uid = "__expr__"
        relative_time_range {
          from = 0
          to   = 0
        }
        model = jsonencode({
          refId      = "C"
          type       = "threshold"
          expression = "B"
          conditions = [{ evaluator = { type = "gt", params = [rule.value.above] } }]
        })
      }
    }
  }
}
