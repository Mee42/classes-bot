import Head from 'next/head'
import Image from 'next/image'
import styles from '../styles/Home.module.scss'
import 'bootstrap/dist/css/bootstrap.css'
import React, {useState} from "react";
import {WEBPAGE_URL} from "./home";


export default function Home() {
    return (
        <div className={styles.container}>
            <Head>
                <title>Classes 2021</title>
                <meta name="description" content="Made by Carson"/>
                <link rel="icon" href="/favicon.ico"/>
            </Head>

            <main className={styles.main}>
                <h1 className={styles.title}>
                    Oakton Classes 2021 Website
                </h1>
                <div style={{margin: "0 auto 0", paddingLeft: "auto", display: "flex", flexDirection: "row", }}>
                    <div style={{marginLeft: "auto"}}><SignInButton/></div>
                    <a className={"btn btn-primary"} style={{margin: "12px", marginRight: "auto"}} href={WEBPAGE_URL + "/home"}>
                        Home
                    </a>
                </div>
            </main>

        </div>
    );
}
export function SignInButton(): JSX.Element {
    return <a
        className={"btn btn-outline-primary"} style={{ margin: "12px"}}
        href={discordURL}>
        Log In
    </a>
}

const discordURL = "https://discord.com/api/oauth2/authorize?client_id=747884756011712705&redirect_uri=https%3A%2F%2Fclasses.carson.sh%2Fapi%2Foauth%2Fredirect&response_type=code&scope=identify"
