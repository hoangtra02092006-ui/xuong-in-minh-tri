import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { createAppRouter } from './router'
import { useOrderStore } from './stores/order'
import './shared/tokens.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
// Step 2 guard: no design in the order sends the user back to step 1 (spec 5.2).
app.use(createAppRouter(() => useOrderStore(pinia).items.length > 0))
app.mount('#app')
