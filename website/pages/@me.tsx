
import styles from '../styles/Home.module.scss'
import {API_URL, Class, getInitialClassesAsync, getUserAsync, Header, User} from './home'
import React, {CSSProperties, useRef, useState} from "react";
import {useRouter} from "next/router";
import cookie from "js-cookie";

type SaveState = "saved" | "saving" | "unsaved" | "error"

export enum Grade { Freshman = "Freshman", Sophomore = "Sophomore", Junior = "Junior", Senior = "Senior" }
const allGrades = [Grade.Freshman, Grade.Sophomore, Grade.Junior, Grade.Senior]

function GradeSelector({ saveState, onEdit, user }: { saveState: SaveState, onEdit: () => void, user: User|null }) {
    const [selection, setSelection] = useState<Grade|null>(user?.grade ?? null)
    if(selection == null && user != null) {
        setSelection(user.grade)
    }
    function button(grade: Grade, style: CSSProperties = {}) {
        return <button id={"grade_" + grade} style={style} className={"btn btn-secondary" + (selection === grade?" active":"")} onClick={() => {
            setSelection(grade);
            onEdit()
        }}>
            {grade}
        </button>
    }

    const [color, text] = {
            "saved": ["green", "Saved"],
            "saving": ["darkorange", "Saving..."],
            "unsaved": ["red", "Unsaved"],
            "error": ["black", "Error Saving"]
    }[saveState]

    return <div className={styles.gradeSelector}>
        <div style={{
            height: "auto",
            width: "150px",
            color: color,
            backgroundColor: "white",
            marginLeft: "auto",
            marginRight: "auto",
            textAlign: "center",
            borderRadius: "15px",
            border: "solid 1px " + color,
            verticalAlign: "center",
            display: "flex",
            alignItems: "center",
            flexDirection: "column",
            justifyContent: "center",
            transitionDuration: "0.5s",
        }}>
            {text}
        </div>
        <div style={{ margin: "0 1em 0", width: "max(20%, 7em)"}}>
            <input id="input_name"
                   type="text"
                   className="form-control"
                   datatype="text"
                   placeholder="Name (First)"
                   defaultValue={user?.name ?? ""}
                   onInput={onEdit}
            />
        </div>
        <div style={{
            display: "flex",
            flexDirection: "row",
            flexWrap: "nowrap",
            margin: "0 auto 0"
        }}>
            {button(Grade.Freshman)}
            {button(Grade.Sophomore)}
            {button(Grade.Junior)}
            {button( Grade.Senior)}
        </div>
    </div>
}

function getSelectedGrade(): Grade {
    return allGrades.find(grade => document.getElementById("grade_" + grade)!!.classList.contains("active")) ?? Grade.Senior
}

function ClassSelector({ onEdit, initialClasses = null }: { onEdit: () => void, initialClasses?: Class[]|null }) {
    return <div className={styles.classesInputList}>
        {[1, 2, 3, 4, 5, 6, 7, 8].map(period =>
            <div key={period} className={styles.classInputLine}>
                <span>Period {period}</span>
                <input id={"input_classname_" + period} type="text" onInput={onEdit} className={"form-control"} datatype={"text"}
                          placeholder={"Class Name"} defaultValue={initialClasses?.[period - 1]?.name ?? ""}
                          style={{margin: "5px"}}/>
                <input id={"input_teacher_" + period} type="text" onInput={onEdit} className={"form-control"} datatype={"text"}
                          placeholder={"Teacher"} defaultValue={initialClasses?.[period - 1]?.teacher ?? ""}
                          style={{margin: "5px"}}/>
                <input id={"input_room_" + period} type={"text"} onInput={onEdit} className={"form-control"} datatype={"text"}
                          placeholder={"Room"} defaultValue={initialClasses?.[period - 1]?.room ?? ""}
                          style={{margin: "5px", width: "30%"}}/>
            </div>
        )}
    </div>
}

function SettingsBox({ user, initialClasses }: { user: User|null, initialClasses?: Class[]|null }) {
    const [saveState, setSaveState] = useState<SaveState>("saved")
    const timeoutHandle = useRef<NodeJS.Timeout | null>(null)
    const onEdit = () => {
        setSaveState("unsaved")
        if(timeoutHandle.current != null) clearTimeout(timeoutHandle.current)
        timeoutHandle.current = setTimeout(function() {
            setSaveState("saving")
            // now we need to actually save
            fetch(API_URL + "/api/user/set-classes",{
                method: "post",
                headers: {
                    auth: cookie.get("auth") ?? ""
                },
                body: JSON.stringify({
                    classes: [1, 2, 3, 4, 5, 6, 7, 8].map(period => ({
                        name: (document.getElementById("input_classname_" + period) as HTMLInputElement).value,
                        teacher: (document.getElementById("input_teacher_" + period) as HTMLInputElement).value,
                        room: (document.getElementById("input_room_" + period) as HTMLInputElement).value,
                        id: -1
                    })),
                    grade: getSelectedGrade(),
                    name: (document.getElementById("input_name") as HTMLInputElement).value
                })
                // todo make this an actual post that saves the data
            })
                .then(e => {
                    if(e.status == 200) {
                        setSaveState("saved")
                    } else {
                        setSaveState("error")
                    }
                })
        }, 1000)
    }
    return <>
        <GradeSelector user={user} saveState={saveState} onEdit={onEdit}/>
        <ClassSelector onEdit={onEdit} initialClasses={initialClasses}/>
    </>
}


export default function Me() {

    const [user, setUser] = useState<User | null>(null)
    const [initialClasses, setInitialClasses] = useState<Class[]|null>(null)

    const router = useRouter()

    React.useEffect(() => getUserAsync(setUser), [])
    React.useEffect(() => getInitialClassesAsync(setInitialClasses), [])

    return <div className={styles.container}>
        <Header user={user} router={router}/>
        <div className={styles.meMainContainer}>
            <h1 className={styles.title}>Your Profile</h1>

            <SettingsBox user={user} initialClasses={initialClasses}/>

            <div className={styles.dangerbox}>
                <button className={"btn btn-outline-danger"} style={{marginLeft: "auto"}} onClick={() => alert("unimplemented")}>Sign out from all locations</button>
                <button className={"btn btn-outline-danger"} onClick={() => alert("unimplemented")}>Sign out from Discord</button>
                <button className={"btn btn-danger"} style={{marginRight: "auto"}} onClick={() => alert("unimplemented")}>Delete Account</button>
            </div>
        </div>
    </div>
}