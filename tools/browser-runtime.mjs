// Development/test runtime resolution without a workstation-specific installation path.
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {existsSync} from 'node:fs';
import {homedir} from 'node:os';
import {join} from 'node:path';
let modulePath=process.env.PLAYWRIGHT_MODULE;
if(!modulePath){
 try{modulePath=createRequire(import.meta.url).resolve('playwright');}
 catch{modulePath=createRequire(process.execPath).resolve('playwright');}
}
const playwright=await import(modulePath.startsWith('file:')?modulePath:pathToFileURL(modulePath).href);
export const {chromium,firefox,webkit}=playwright.default||playwright;
export function adbExecutable(){
 if(process.env.ADB)return process.env.ADB;
 const sdk=process.env.ANDROID_SDK_ROOT||process.env.ANDROID_HOME||(process.platform==='win32'?join(homedir(),'AppData','Local','Android','Sdk'):null);
 const candidate=sdk&&join(sdk,'platform-tools',process.platform==='win32'?'adb.exe':'adb');
 return candidate&&existsSync(candidate)?candidate:'adb';
}
