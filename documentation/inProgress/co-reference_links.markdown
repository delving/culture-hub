# Co-Reference Links

Co


## XML design of a Co-Reference link

    <link>
     <link_uri/>
     <link_category/> # LINK | CO_REF

     <who>
       <user_uri>
       <name/>
       <organisation/>
       <role/>
     </who>

     <from>
       <from_uri/>
       <from_crm_class/>
       <from_type/>
     </from>

     <to>
       <to_uri/>
       <to_crm_class/>
       <to_type/>
     </to>


     <when>
         <created_date/>
     <when>

     <where>
      <site_uri/> # Defined in Commons
      <context/> # Objects, Collections, Stories, Essays, Articles etc (context at the creator site)
      <site_type/> # CULTUREHUB | OTHER_KNOWN TYPES | UNKNOWN
     </where>

     <why>
         <type/>  # tag | comment | note | Same_As, Part_Of etc
         <value/> # value of above if text is needed, string
         <note/> # note for above
         <quality/> # 100%, 50%, 25% for Same_As etc later on
      </why>

    </link>
