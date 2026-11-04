// Simulated phone positions are separate from the reviewed residential buildings.
// Each neighborhood has 48 spaced slots: 24 for residents and 24 for attendees.
export const communityLongitudeCuts=[135.760,135.7644];
export const demoCommunityAt=lon=>lon<communityLongitudeCuts[0]?'demo-west':lon<communityLongitudeCuts[1]?'demo-central':'demo-east';
export function demoPhonePoint(community,index){
 const bounds={'demo-west':[135.758,135.760],'demo-central':[135.760,135.7644],'demo-east':[135.7644,135.767]};
 if(!bounds[community]||!Number.isInteger(index)||index<0||index>=48)throw Error('Invalid demo map slot');
 const [west,east]=bounds[community],slot=(index*17)%48,col=slot%4,row=Math.floor(slot/4);
 return {latitude:35.01125+row*(.0065/11),longitude:west+(east-west)*(col+.5)/4};
}
