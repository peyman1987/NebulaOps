{{- define "springMvcAddon.name" -}}
spring-mvc-service
{{- end -}}

{{- define "springMvcAddon.labels" -}}
app: spring-mvc-service
app.kubernetes.io/name: spring-mvc-service
app.kubernetes.io/part-of: nebulaops
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end -}}
