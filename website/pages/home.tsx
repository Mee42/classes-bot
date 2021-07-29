import Head from 'next/head'
import styles from '../styles/Home.module.scss'
import 'bootstrap/dist/css/bootstrap.css'
import React, {useState} from "react";
import {NextRouter, useRouter} from 'next/router';
import cookie from 'js-cookie';
import {Grade} from "./@me";
import {SignInButton} from "./index";


const authToken = cookie.get("auth")

function logOut(router: NextRouter) {
  cookie.set("auth", "")
  router.push("http://localhost:3000")
}


export function getUserAsync(setUser: (user: User|null) => void, isFailureOkay: boolean = false) {
    if(isFailureOkay && authToken === "") return;
    fetch("http://localhost:7000/api/users/@me", {
      headers: [ ["auth", authToken ?? "" ] ]
    })
        .then(e => {
          if(e.status != 200) {
            alert("ERROR 1: " + e.status + " " + e.statusText)
          }
          return e
        })
        .then(x => x.json())
        .then(x => x as User)
        .catch(e => {console.log(e); return null})
        .then(e => setUser(e))
}
// undefined = do it
// null = don't
export function getInitialClassesAsync(setInitialClasses: (initialClasses: Class[]|null) => void, id: string|undefined = undefined) {
    fetch("http://localhost:7000/api/classes/" + (id===undefined?"@me":id), {
      headers: [ ["auth", authToken ?? "" ] ]
    })
        .then(e => {
          if(e.status != 200) {
            alert("ERROR: " + e.status + " " + e.statusText)
          }
          return e
        })
        .then(x => x.json())
        .then(x => x as Class[])
        .catch(e => {console.log(e); return null})
        .then(e => setInitialClasses(e))
}

export function Header(props: { user: User | null, router: NextRouter}) {
  return <div style={{
    display: "flex",
    flexDirection: "row",
    padding: "15px",
    width: "100%",
    // height: "75px",
    backgroundColor: "white",
    borderBottom: "black 1px solid"
  }}>
    <button className={styles.headerbox + " btn btn-primary"} onClick={() => props.router.push("http://localhost:3000/home")}>Home</button>
      {
          props.user === null ?  <div/> : <a className={styles.headerbox + " btn btn-outline-primary"} style={{marginLeft: "auto"}} href={"http://localhost:3000/user/" + props.user.id}>Schedule</a>
      }
      {
          props.user === null ?  <div/> : <a className={styles.headerbox + " btn btn-outline-primary"} href={"http://localhost:3000/@me"}>Settings</a>
      }

      {
          props.user === null ? <div/> : <button className={styles.headerbox + " btn btn-outline-primary"} onClick={() => logOut(props.router)}>Log Out</button>
      }
  </div>
}

export function renderName(user: User) {
  if(user === undefined) return
  const content = user.name || (user.username + "#" + user.discrim)
  return content
}
export function renderName2(userID: string, users: User[]) {
  return renderName(users!!.find(u => u.id === userID)!!)
}


function ClassesList({ meID, user }:{ meID?: string|null, user: User|null}) {
  const [classes, setClasses] = useState<Class[]|null>(null)
  const [periods, setPeriods] = useState<Period[]|null>(null)
  const [users, setUsers] = useState<User[]|null>(null)
  const [filterForSelf, setFilterForSelf] = useState(false)

  React.useEffect(() => {
    fetch("http://localhost:7000/api/classes")
        .then(x => x.json())
        .then(x => setClasses(x as Class[]))
  }, [])
  React.useEffect(() => {
    fetch("http://localhost:7000/api/periods")
        .then(x => x.json())
        .then(x => setPeriods(x as Period[]))
  }, [])
  React.useEffect(() => {
    fetch("http://localhost:7000/api/users")
        .then(x => x.json())
        .then(x => setUsers(x as User[]))
  }, [])


  if(classes === null || users === null || periods === null) return <></>
  // assuming sectionBy = period

  return <div style={{display: "flex", flexDirection: "column"}}>
    <div style={{margin: "0 auto 0"}}>
      {/* button board*/}
        {user === null ? <></> : <button
            className={"btn "  + (filterForSelf?"btn-primary ": "btn-secondary ")}
            onClick={() => setFilterForSelf(!filterForSelf) }
        >
          Filter for self
        </button>}
    </div>
    <div>
      {classes.map(clazz => {
        if(meID !== null && filterForSelf && periods.find(period => period.class == clazz.id && period.user == meID) === undefined) {
          return <div key={clazz.id}/>
        }
        const periodInfo = [1, 2, 3, 4, 5, 6, 7, 8].map(periodNumber => {
          const peopleWhoMatch = periods.filter(period => period.period === periodNumber && period.class === clazz.id)
          return peopleWhoMatch.length === 0 ? <div key={periodNumber}/> :
              <div key={periodNumber}>
                Period {periodNumber}
                <div style={{marginLeft: "auto", width: "min-content"}}>
                  {peopleWhoMatch.map(period => <div key={period.user}>{renderName2(period.user, users)}</div>)}
                </div>
              </div>
        })
        return renderClass(clazz, <span>({clazz.id})</span>, periodInfo)
        }
      )}
    </div>
  </div>
}

export function renderClass(clazz: Class, preludingContent: JSX.Element, innerContent: JSX.Element|JSX.Element[], index: number|null = null) {
  const id = clazz.id
  return <div
      style={{
        backgroundColor: "white",
        borderRadius: "10px",
        border: "lightgrey 3px solid",
        margin: "10px",
        padding: "10px",
        color: "black"
      }}
      key={index==null?clazz.id:index}>
    <h3>{preludingContent}{clazz.name} - <i>{clazz.teacher}</i> - {clazz.room}</h3>
    <div>
        {innerContent}
    </div>

  </div>
}


export default function Home() {

  const [user, setUser] = useState<User | null>(null)
  React.useEffect(() => getUserAsync(setUser, true), [])



  const router = useRouter()
    return (
        <div className={styles.container}>
      <Head>
        <title>Classes 2021</title>
        <meta name="description" content="Home Page" />
        <link rel="icon" href="/favicon.ico" />
      </Head>


      <Header user={user} router={router}/>



      <main className={styles.main}>


        <h1 className={styles.title}>
            { user === null ? <h3> Sign in to add your classes<SignInButton/></h3> : (<span>Welcome, {user?.username ?? ""}#{user?.discrim ?? ""}</span>) }
        </h1>

        <ClassesList meID={user?.id} user={user}/>
      </main>



    </div>
  )
}

export type User = { username: string, id: string, discrim: string, grade: Grade, name: string }
export type Class = { id: number, name: string, teacher: string, room: string }
export type Period = { class: number, user: string, period: number }