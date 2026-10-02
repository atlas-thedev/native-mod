const http=require('http');
const A='a'.repeat(64),B='b'.repeat(64);
http.createServer((req,res)=>{
  if(req.url.startsWith('/v1/skins/directory')){res.writeHead(200,{'Content-Type':'application/json'});
    return res.end(JSON.stringify({ok:true,epoch:1,rev:1,full:true,textureBase:'http://127.0.0.1:8099/csl/textures/',entries:[{n:'TestAlice',m:'slim',s:A,c:B,u:null,t:1,r:1}]}));}
  if(req.url.startsWith('/v1/skins/stream')){res.writeHead(200,{'Content-Type':'text/event-stream'});res.write('event: hello\ndata: {"epoch":1,"rev":1,"resync":false}\n\n');return;}
  res.writeHead(404);res.end();
}).listen(8099,'127.0.0.1');
