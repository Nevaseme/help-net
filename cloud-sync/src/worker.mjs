import {DurableObject} from 'cloudflare:workers';
import {Journal} from './journal.mjs';
import {ApiError} from './validation.mjs';
import {handle} from './http.mjs';

export class GroupJournal extends DurableObject {
 constructor(ctx,env){
  super(ctx,env);
  this.groups=env.GROUPS;
  this.journal=new Journal({
   all:(query,...args)=>ctx.storage.sql.exec(query,...args).toArray(),
   run:(query,...args)=>{ctx.storage.sql.exec(query,...args);},
   transaction:fn=>ctx.storage.transactionSync(fn),
   flush:()=>ctx.storage.sync()
  });
 }
 async dispatch(operation,token,args){
  try{
   let body;
   switch(operation){
    case 'directoryConfigure':case 'directoryAccess':body=await this.journal[operation](args,async(group,method,input,auth)=>{
     const result=await this.groups.getByName(group).dispatch(method,auth,input);
     if(result.status!==200)throw new ApiError(result.status,result.body.error.code,result.body.error.message);
     return result.body;
    });break;
    case 'setMemberPermission':case 'setVenueClock':case 'access':case 'recoverVenue':case 'authorizeVenue':case 'configureVenue':case 'checkVenue':case 'joinVenue':body=await this.journal[operation](token,args);break;
    case 'create':body=await this.journal.create(args);break;
    case 'join':body=await this.journal.join(args);break;
    case 'invite':body=await this.journal.invite(token);break;
    case 'roster':body=await this.journal.roster(token);break;
    case 'setMunicipality':body=await this.journal.setMunicipality(token,args);break;
    case 'nearby':body=await this.journal.nearby(token);break;
    case 'relayKeys':body=await this.journal.relayKeys(token);break;
    case 'registerRelayKey':body=await this.journal.registerRelayKey(token,args);break;
    case 'push':body=await this.journal.push(token,args);break;
    case 'pull':body=await this.journal.pull(token,args.after,args.limit);break;
    default:throw new ApiError(404,'not_found');
   }
   return {status:200,body};
  }catch(e){
   if(e instanceof ApiError)return {status:e.status,body:{error:{code:e.code,message:e.message}}};
   // Never log record bodies, tokens, invitation codes, names or positions.
   console.error(JSON.stringify({event:'journal_failure',operation}));
   return {status:503,body:{error:{code:'storage_unavailable',message:'保存を確認できませんでした。同じ記録を再送してください。'}}};
  }
 }
}
export default {fetch:handle};
