import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { createAppRouter } from './router'
import './shared/tokens.css'

const app = createApp(App)
app.use(createPinia())
// The upload store (built in the nesting card) replaces this with a real "has items" check.
app.use(createAppRouter(() => false))
app.mount('#app')
