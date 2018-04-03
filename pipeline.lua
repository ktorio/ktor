init = function(args)
   request_uri = args[1]
   depth = 10 -- tonumber(args[2]) or 1

   local r = {}
   for i=1,depth do
     r[i] = wrk.format(nil, request_uri)
   end
   req = table.concat(r)
end

request = function()
   wrk.headers["J-Tenant-Id"] = "1007"
   return req
end
