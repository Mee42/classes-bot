import Head from 'next/head'
import styles from '../styles/Home.module.scss'
import 'bootstrap/dist/css/bootstrap.css'
import React, {useState} from "react";
import {NextRouter, useRouter} from 'next/router';
import cookie from 'js-cookie';
import {Grade} from "./@me";
import {SignInButton} from "./index";
import {fail} from "assert";


const authToken = cookie.get("auth")

export const API_URL = "https://classes.carson.sh"
export const WEBPAGE_URL = "https://classes.carson.sh"


function logOut(router: NextRouter) {
  cookie.set("auth", "")
  router.push(WEBPAGE_URL)
}


export function getUserAsync(setUser: (user: User|null) => void, isFailureOkay: boolean = false, id: string|null = null) {
    fetch(API_URL + "/api/users/" + (id ?? "@me"), {
      headers: [ ["auth", authToken ?? "" ] ]
    })
        .then(e => {
          if(e.status != 200) {
              if(!isFailureOkay) {
                  alert("ERROR 1: " + e.status + " " + e.statusText)
                  throw new Error("no")
              }
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
    fetch(API_URL + "/api/classes/" + (id===undefined?"@me":id), {
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
      position: "fixed",
      top: 0,
      left: 0,
    // height: "75px",
    backgroundColor: "white",
    borderBottom: "black 1px solid",
  }} className="header-div">
    <button className={styles.headerbox + " btn btn-primary"} onClick={() => props.router.push(WEBPAGE_URL + "/home")}>Home</button>
      {
          props.user === null ?  <div/> : <a className={styles.headerbox + " btn btn-outline-primary"} style={{margin: "0 1em 0 auto"}} href={WEBPAGE_URL + "/user/" + props.user.id}>Schedule</a>
      }
      {
          props.user === null ?  <div/> : <a className={styles.headerbox + " btn btn-outline-primary"} href={WEBPAGE_URL + "/@me"} style={{margin: "0 15px 0 0"}}>Settings</a>
      }

      <button className={styles.headerbox + " btn btn-outline-danger"} onClick={() => logOut(props.router)} style={{ whiteSpace: "nowrap", margin: "0 15px 0 0"}}>Log Out</button>

  </div>
}

export function renderName(user: User) {
    if(user === undefined) return
    const content = user.name || (user.username + "#" + user.discrim)
    return <span><a href={WEBPAGE_URL + "/user/" + user.id}>{content}</a></span>
}
export function renderName2(userID: string, users: User[]) {
  return renderName(users!!.find(u => u.id === userID)!!)
}



function handleError(e: Response) : Response {
    alert("ERROR: " + e.status + ": " + e.statusText)
    return e
}
function ClassesList({ meID, user }:{ meID?: string|null, user: User|null}) {
    const [refreshButtonState, setRefreshButtonState] = useState<"Refresh"|"Refreshing">("Refreshing")
  const [classes, setClasses] = useState<Class[]|null>(null)
  const [periods, setPeriods] = useState<Period[]|null>(null)
  const [users, setUsers] = useState<User[]|null>(null)
  const [filterForSelf, setFilterForSelf] = useState(false)


    React.useEffect(() => {
        if(refreshButtonState === "Refreshing") {
            const a = fetch(API_URL + "/api/classes")
                .then(e => e.status === 200 ? e : handleError(e))
                .then(x => x.json())
                .then(x => setClasses(x as Class[]))
            const b = fetch(API_URL + "/api/periods")
                .then(e => e.status === 200 ? e : handleError(e))
                .then(x => x.json())
                .then(x => setPeriods(x as Period[]))
            const c = fetch(API_URL + "/api/users")
                .then(e => e.status === 200 ? e : handleError(e))
                .then(x => x.json())
                .then(x => setUsers(x as User[]))
            Promise.all([a, b, c]).then(e => setRefreshButtonState("Refresh"))
        }
    }, [refreshButtonState])


  if(classes === null || users === null || periods === null) return <></>
  // assuming sectionBy = period

  return <div style={{display: "flex", flexDirection: "column", width: "min(800px, 90%)"}}>
    <div style={{margin: "0 auto 0"}}>
      {/* button board*/}
        {user === null ? <></> : <button
            className={"btn "  + (filterForSelf?"btn-primary ": "btn-secondary ")}
            onClick={() => setFilterForSelf(!filterForSelf) }
        >
          Filter for self
        </button>}
        <button
            className={"btn btn-primary"}
            onClick={() => {
                setRefreshButtonState("Refreshing")
            }}
            style={{ marginLeft: "10px" }}
        >
            {refreshButtonState}
        </button>
    </div>
    <div style={{display: "flex", flexDirection: "column", alignItems: "center", width: "min(800px, 90%)"}}>
      {classes.map(clazz => {
        if(meID !== null && filterForSelf && periods.find(period => period.class == clazz.id && period.user == meID) === undefined) {
          return <div key={clazz.id}/>
        }
        const periodInfo = [1, 2, 3, 4, 5, 6, 7, 8].map(periodNumber => {
          const peopleWhoMatch = periods.filter(period => period.period === periodNumber && period.class === clazz.id)
          return peopleWhoMatch.length === 0 ? <div key={periodNumber}/> :
              <div key={periodNumber}>
                <div style={{display: "flex", flexDirection: "row", alignItems: "center"}}>
                    Period {periodNumber}
                    <div style={{borderBottom: "1px solid black", flexGrow: 1, height: "1px", marginLeft: "30px", marginRight: "30px"}}/>
                </div>
                <div style={{marginLeft: "auto", width: "min-content"}}>
                  {peopleWhoMatch.map(period => <div key={period.user}>{renderName2(period.user, users)}</div>)}
                </div>
              </div>
        })
          return renderClass(clazz,<span/>, periodInfo)
        }
      )}
    </div>
  </div>
}

export function renderClass(clazz: Class, preludingContent: JSX.Element, innerContent: JSX.Element|JSX.Element[], index: number|null = null) {
  return <div
      style={{
        backgroundColor: "white",
        borderRadius: "10px",
        border: "lightgrey 3px solid",
        margin: "10px",
        padding: "10px",
        color: "black",
          width: "min(800px, 90%)"
      }}
      key={index==null?clazz.id:index}>
      {preludingContent}
      <div style={{display: "flex", flexDirection: "row", alignItems: "center"}}>
          <h2>{clazz.name}</h2>
          <span style={{width: "10px"}}>    </span>
          <h3 style={{marginRight: "10px"}}><i>{clazz.teacher}</i></h3>
          <span style={{marginLeft: "auto"}}>Room {clazz.room}</span>
      </div>
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


    { user === null ? <h3 className={styles.title}> Sign in to add your classes<SignInButton/></h3> : (<h1 className={styles.title}>Welcome, {renderName(user)}</h1>) }

        <ClassesList meID={user?.id} user={user}/>
      </main>



    </div>
  )
}

export type User = { username: string, id: string, discrim: string, grade: Grade, name: string }
export type Class = { id: number, name: string, teacher: string, room: string }
export type Period = { class: number, user: string, period: number }