import { createFileRoute } from '@tanstack/solid-router'

export const Route = createFileRoute('/this-will-crash-the-app')({
    component: RouteComponent,
})

function RouteComponent() {
    throw new Error('explode explode explode')
}
