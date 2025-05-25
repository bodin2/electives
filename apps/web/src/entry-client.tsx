// @refresh reload
import { StartClient, mount } from '@solidjs/start/client'

// biome-ignore lint/style/noNonNullAssertion: This is a required element in the HTML
mount(() => <StartClient />, document.getElementById('app')!)
