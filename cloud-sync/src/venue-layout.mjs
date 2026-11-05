import phonePoints from './venue-phone-points.json' with {type:'json'};
// Bounded neighborhoods cover about half of the previous 0.007-degree strips.
// All reviewed residential footprints stay inside their original neighborhood.
export const communityLongitudeCuts=[135.760,135.7644];
export const demoCommunities=[
 {id:'demo-west',latitude:35.013,longitude:135.7587,nameJa:'御所南西町内会',nameEn:'Gosho South West',boundary:[[135.758,35.0105],[135.7594,35.0105],[135.7594,35.0155],[135.758,35.0155]]},
 {id:'demo-central',latitude:35.01295,longitude:135.7622,nameJa:'御所南中央町内会',nameEn:'Gosho South Central',boundary:[[135.760,35.0109],[135.7644,35.0109],[135.7644,35.015],[135.760,35.015]]},
 {id:'demo-east',latitude:35.0153,longitude:135.7656,nameJa:'御所南東町内会',nameEn:'Gosho South East',boundary:[[135.7644,35.0109],[135.7655,35.0109],[135.7655,35.0144],[135.767,35.0144],[135.767,35.0169],[135.7644,35.0169]]}
];
export const demoCommunityAt=lon=>lon<communityLongitudeCuts[0]?'demo-west':lon<communityLongitudeCuts[1]?'demo-central':'demo-east';
/** Fixed points reviewed on road centerlines; no runtime avoidance or re-positioning. */
export function demoPhonePoint(community,index){
 const point=phonePoints.points[community]?.[index];
 if(!Number.isInteger(index)||!point)throw Error('Invalid demo map slot');
 return {...point};
}
