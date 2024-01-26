{{- define "nebulaops.name" -}}nebulaops{{- end -}}
{{- define "nebulaops.fullname" -}}{{ .Release.Name }}-{{ include "nebulaops.name" . }}{{- end -}}
{{- define "nebulaops.labels" -}}
app.kubernetes.io/name: {{ include "nebulaops.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: nebulaops-{{ .Chart.Version }}
{{- end -}}
