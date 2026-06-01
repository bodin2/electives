import { createContext, type ParentProps, useContext } from 'solid-js'

export interface GroupDisplayContext {
    editable: boolean
}

const GroupDisplayContext = createContext<GroupDisplayContext>(null as unknown as GroupDisplayContext)

export function GroupDisplayContextProvider(props: ParentProps<{ value: GroupDisplayContext }>) {
    return <GroupDisplayContext.Provider value={props.value}>{props.children}</GroupDisplayContext.Provider>
}

export const useGroupDisplayContext = () => useContext(GroupDisplayContext) ?? BaseGroupDisplayContext

export const BaseGroupDisplayContext: GroupDisplayContext = {
    editable: false,
}
