import {useRouter} from "next/router";
import styles from '../../styles/Home.module.scss'
import React, {useState} from "react";
import {Class, getInitialClassesAsync, getUserAsync, Header, renderClass, renderName, User} from "../home";

export default function Main() {
    const router = useRouter()
    const [user, setUser] = useState<User | null>(null)
    const [shownUser, setShownUser] = useState<User|null>(null)
    const [classes, setClasses] = useState<Class[]|null>(null)


    React.useEffect(() => {
        const { id } = router.query
        if(id === undefined) return
        getUserAsync(setUser, true)
        getInitialClassesAsync(setClasses, id as string)
        getUserAsync(setShownUser, false, id as string)
    }, [router.query])

    return <div>
        <Header user={user} router={router}/>
        <div style={{margin: "100px auto 0"}}>
            {
                shownUser === null ? <h2/> : <h2 style={{margin: "0 auto 0", textAlign: "center"}}>{renderName(shownUser)}&apos;s Schedule</h2>
            }
            <div style={{width: "min(90%, 600px)", margin: "0 auto 0"}}>
                { classes?.map((clazz, i) => renderClass(clazz, <span>{ordinal_suffix_of(i + 1)} Period: </span>, <div/>, i)) }
            </div>
        </div>
    </div>
}


//https://stackoverflow.com/questions/13627308/add-st-nd-rd-and-th-ordinal-suffix-to-a-number
function ordinal_suffix_of(i: number) {
    const ones = i % 10
    const tens = i % 100;
    if (ones == 1 && tens != 11) {
        return i + "st";
    }
    if (ones == 2 && tens != 12) {
        return i + "nd";
    }
    if (ones == 3 && tens != 13) {
        return i + "rd";
    }
    return i + "th";
}

