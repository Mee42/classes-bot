import {useRouter} from "next/router";
import styles from '../../styles/Home.module.scss'
import {Head} from "next/document";
import React, {useState} from "react";
import {Class, getInitialClassesAsync, getUserAsync, Header, renderClass, renderName, User} from "../home";

export default function Main() {
    const router = useRouter()
    const [user, setUser] = useState<User | null>(null)
    const [classes, setClasses] = useState<Class[]|null>(null)


    React.useEffect(() => {
        const { id } = router.query
        if(id === undefined) return
        getUserAsync(setUser, true)
        getInitialClassesAsync(setClasses, id as string)
    }, [router.query])

    return <div>
        <Header user={user} router={router}/>
        <div style={{margin: "200px auto 0"}}>
            <h2 style={{margin: "0 auto 0", textAlign: "center"}}>{user == null ? "" : renderName(user)}'s Schedule</h2>
            <div style={{width: "max(50%, 600px)", margin: "0 auto 0"}}>
                { classes?.map((clazz, i) => renderClass(clazz, <span>{i + 1}st period: </span>, <div/>, i)) }
            </div>
        </div>




    </div>
}
export function getInitialProps() {}