# Co-Reference Links

Co


## XML design of a Co-Reference link

    <link>
     <link_id>URI</link_ref_id>
     <link_category> </link_category>  

     <who>
       <user_uri/>
       <user_info>
         <name>
         <organisation/>
       </user_info>
     </who>

     <from>
       <uri>[uri]</uri>
       <co-ref extra>
         <sticky stuff like birthplace, death place, born, dead for actors and hierarchy for places etc>
       </co-ref extra>  
     </from>

     <to>
       <uri>[uri]</uri>
       <co-ref extra>
         <sticky stuff like birthplace, death place, born, dead for actors and hierarchy for places etc>
       </co-ref extra>  
     </to>


     <when>
         <created_date/>
     <when>

     <where>
      <context>Objects, Collections, Stories etc<context>
      <site>CultureHub_id</site>
     </where>

     <why>
       <for coref>
        <statement by creator>comment on why<statement by creator>
        <relation>Same_As etc</relation>
        <relation_quality>Sure! | Likely | well, i think it could be this way etc | </relation_quality>
       </for coref>
       <for links>
         <type | tag | comment | etc >
         <value/>
         <note/>
         <context  | object | Collections | Stories etc >
       </for links>
      </why>

    </link>