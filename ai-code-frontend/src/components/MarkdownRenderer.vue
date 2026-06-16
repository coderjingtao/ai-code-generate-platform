<template>
  <div class="markdown-content" v-html="renderedMarkdown"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

// Import dark-mode syntax highlight style
import 'highlight.js/styles/github-dark.css'

interface Props {
  content: string
}

const props = defineProps<Props>()

// Configure markdown-it instance
const md: MarkdownIt = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: function (str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return (
          '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>'
        )
      } catch {
        // ignore and fallback
      }
    }

    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  },
})

// Compute rendered markdown HTML
const renderedMarkdown = computed(() => {
  return md.render(props.content)
})
</script>

<style scoped>
.markdown-content {
  line-height: 1.65;
  color: inherit;
  word-wrap: break-word;
}

/* Deep stylings for generated v-html elements compatible with dark mode */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 1.25em 0 0.5em 0;
  font-weight: 600;
  line-height: 1.25;
  color: var(--ac-text);
}

.markdown-content :deep(h1) {
  font-size: 1.4em;
  border-bottom: 1px solid var(--ac-border);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.2em;
  border-bottom: 1px solid var(--ac-border);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h3) {
  font-size: 1.1em;
}

.markdown-content :deep(p) {
  margin: 0.6em 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.5em;
}

.markdown-content :deep(li) {
  margin: 0.3em 0;
}

.markdown-content :deep(blockquote) {
  margin: 1em 0;
  padding: 0.5em 1em;
  border-left: 4px solid var(--ac-primary);
  background-color: var(--ac-surface-muted);
  color: var(--ac-text-muted);
  border-radius: 0 4px 4px 0;
}

.markdown-content :deep(code) {
  background-color: rgba(23, 24, 29, 0.06);
  color: var(--ac-text);
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background-color: var(--ac-code-bg);
  border: 1px solid var(--ac-code-header-border);
  border-radius: 6px;
  padding: 1em;
  overflow-x: auto;
  margin: 1em 0;
}

.markdown-content :deep(pre code) {
  background-color: transparent;
  color: inherit;
  padding: 0;
  border-radius: 0;
  font-size: 0.9em;
  line-height: 1.4;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  margin: 1em 0;
  width: 100%;
}

.markdown-content :deep(table th),
.markdown-content :deep(table td) {
  border: 1px solid var(--ac-border);
  padding: 0.5em 0.8em;
  text-align: left;
}

.markdown-content :deep(table th) {
  background-color: #1e293b;
  color: var(--ac-text);
  font-weight: 600;
}

.markdown-content :deep(table tr:nth-child(even)) {
  background-color: rgba(0, 0, 0, 0.02);
}

.markdown-content :deep(a) {
  color: #3b82f6;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 0.5em 0;
}

.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--ac-border);
  margin: 1.5em 0;
}

/* Override hljs styling background inside block to merge with container */
.markdown-content :deep(.hljs) {
  background: transparent !important;
  padding: 0 !important;
}
</style>
