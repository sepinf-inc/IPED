
dofile("./AI/Const.lua")
dofile("./AI/Util.lua")

-----------------------------
-- state
-----------------------------
IDLE_ST					= 0
FOLLOW_ST				= 1
CHASE_ST				= 2
ATTACK_ST				= 3
MOVE_CMD_ST				= 4
STOP_CMD_ST				= 5
ATTACK_OBJECT_CMD_ST			= 6
ATTACK_AREA_CMD_ST			= 7
PATROL_CMD_ST				= 8
HOLD_CMD_ST				= 9
SKILL_OBJECT_CMD_ST			= 10
SKILL_AREA_CMD_ST			= 11
FOLLOW_CMD_ST				= 12
----------------------------


------------------------------------------
-- global variable
------------------------------------------
MyState				= IDLE_ST	-- 최초의 상태는 휴식
MyEnemy				= 0		-- 적 id
MyDestX				= 0		-- 목적지 x
MyDestY				= 0		-- 목적지 y
MyPatrolX			= 0		-- 정찰 목적지 x
MyPatrolY			= 0		-- 정찰 목적지 y
ResCmdList			= List.new()	-- 예약 명령어 리스트 
MyID				= 0		-- 호문클루스 id
MySkill				= 0		-- 호문클루스의 스킬
MySkillLevel		= 0		-- 호문클루스의 스킬 레벨
------------------------------------------


------------- command process  ---------------------

function	OnMOVE_CMD (x,y)
	
	TraceAI ("OnMOVE_CMD")

	if ( x == MyDestX and y == MyDestY and MOTION_MOVE == GetV(V_MOTION,MyID)) then
		return		-- 현재 이동중인 목적지와 같은 곳이면 처리하지 않는다. 
	end

	local curX, curY = GetV (V_POSITION,MyID)
	if (math.abs(x-curX)+math.abs(y-curY) > 15) then		-- 목적지가 일정 거리 이상이면 (서버에서 먼거리는 처리하지 않기 때문에)
		List.pushleft (ResCmdList,{MOVE_CMD,x,y})			-- 원래 목적지로의 이동을 예약한다. 	
		x = math.floor((x+curX)/2)							-- 중간지점으로 먼저 이동한다.  
		y = math.floor((y+curY)/2)							-- 
	end

	Move (MyID,x,y)	
	
	MyState = MOVE_CMD_ST
	MyDestX = x
	MyDestY = y
	MyEnemy = 0
	MySkill = 0

end



function	OnSTOP_CMD ()

	TraceAI ("OnSTOP_CMD")

	if (GetV(V_MOTION,MyID) ~= MOTION_STAND) then
		Move (MyID,GetV(V_POSITION,MyID))
	end
	MyState = IDLE_ST
	MyDestX = 0
	MyDestY = 0
	MyEnemy = 0
	MySkill = 0

end



function	OnATTACK_OBJECT_CMD (id)

	TraceAI ("OnATTACK_OBJECT_CMD")

	MySkill = 0
	MyEnemy = id
	MyState = CHASE_ST

end



function	OnATTACK_AREA_CMD (x,y)

	TraceAI ("OnATTACK_AREA_CMD")

	if (x ~= MyDestX or y ~= MyDestY or MOTION_MOVE ~= GetV(V_MOTION,MyID)) then
		Move (MyID,x,y)	
	end
	MyDestX = x
	MyDestY = y
	MyEnemy = 0
	MyState = ATTACK_AREA_CMD_ST
	
end



function	OnPATROL_CMD (x,y)

	TraceAI ("OnPATROL_CMD")

	MyPatrolX , MyPatrolY = GetV (V_POSITION,MyID)
	MyDestX = x
	MyDestY = y
	Move (MyID,x,y)
	MyState = PATROL_CMD_ST

end



function	OnHOLD_CMD ()

	TraceAI ("OnHOLD_CMD")

	MyDestX = 0
	MyDestY = 0
	MyEnemy = 0
	MyState = HOLD_CMD_ST

end



function	OnSKILL_OBJECT_CMD (level,skill,id)

	TraceAI ("OnSKILL_OBJECT_CMD")

	MySkillLevel = level
	MySkill = skill
	MyEnemy = id
	MyState = CHASE_ST

end



function	OnSKILL_AREA_CMD (level,skill,x,y)

	TraceAI ("OnSKILL_AREA_CMD")

	Move (MyID,x,y)
	MyDestX = x
	MyDestY = y
	MySkillLevel = level
	MySkill = skill
	MyState = SKILL_AREA_CMD_ST
	
end



function	OnFOLLOW_CMD ()

	-- 대기명령은 대기상태와 휴식상태를 서로 전환시킨다. 
	if (MyState ~= FOLLOW_CMD_ST) then
		MoveToOwner (MyID)
		MyState = FOLLOW_CMD_ST
		MyDestX, MyDestY = GetV (V_POSITION,GetV(V_OWNER,MyID))
		MyEnemy = 0 
		MySkill = 0
		TraceAI ("OnFOLLOW_CMD")
	else
		MyState = IDLE_ST
		MyEnemy = 0 
		MySkill = 0
		TraceAI ("FOLLOW_CMD_ST --> IDLE_ST")
	end

end



function	ProcessCommand (msg)

	if		(msg[1] == MOVE_CMD) then
		OnMOVE_CMD (msg[2],msg[3])
		TraceAI ("MOVE_CMD")
	elseif	(msg[1] == STOP_CMD) then
		OnSTOP_CMD ()
		TraceAI ("STOP_CMD")
	elseif	(msg[1] == ATTACK_OBJECT_CMD) then
		OnATTACK_OBJECT_CMD (msg[2])
		TraceAI ("ATTACK_OBJECT_CMD")
	elseif	(msg[1] == ATTACK_AREA_CMD) then
		OnATTACK_AREA_CMD (msg[2],msg[3])
		TraceAI ("ATTACK_AREA_CMD")
	elseif	(msg[1] == PATROL_CMD) then
		OnPATROL_CMD (msg[2],msg[3])
		TraceAI ("PATROL_CMD")
	elseif	(msg[1] == HOLD_CMD) then
		OnHOLD_CMD ()
		TraceAI ("HOLD_CMD")
	elseif	(msg[1] == SKILL_OBJECT_CMD) then
		OnSKILL_OBJECT_CMD (msg[2],msg[3],msg[4],msg[5])
		TraceAI ("SKILL_OBJECT_CMD")
	elseif	(msg[1] == SKILL_AREA_CMD) then
		OnSKILL_AREA_CMD (msg[2],msg[3],msg[4],msg[5])
		TraceAI ("SKILL_AREA_CMD")
	elseif	(msg[1] == FOLLOW_CMD) then
		OnFOLLOW_CMD ()
		TraceAI ("FOLLOW_CMD")
	end
end




-------------- state process  --------------------


function	OnIDLE_ST ()
	
	TraceAI ("OnIDLE_ST")

	local cmd = List.popleft(ResCmdList)
	if (cmd ~= nil) then		
		ProcessCommand (cmd)	-- 예약 명령어 처리 
		return 
	end

	local	object = GetOwnerEnemy (MyID)
	if (object ~= 0) then							-- MYOWNER_ATTACKED_IN
		MyState = CHASE_ST
		MyEnemy = object
		TraceAI ("IDLE_ST -> CHASE_ST : MYOWNER_ATTACKED_IN")
		return 
	end

	object = GetMyEnemy (MyID)
	if (object ~= 0) then							-- ATTACKED_IN
		MyState = CHASE_ST
		MyEnemy = object
		TraceAI ("IDLE_ST -> CHASE_ST : ATTACKED_IN")
		return
	end

	local distance = GetDistanceFromOwner(MyID)
	if ( distance > 3 or distance == -1) then		-- MYOWNER_OUTSIGNT_IN
		MyState = FOLLOW_ST
		TraceAI ("IDLE_ST -> FOLLOW_ST")
		return
	end

end



function	OnFOLLOW_ST ()

	TraceAI ("OnFOLLOW_ST")

	if (GetDistanceFromOwner(MyID) <= 3) then		--  DESTINATION_ARRIVED_IN 
		MyState = IDLE_ST
		TraceAI ("FOLLOW_ST -> IDLW_ST")
		return
	elseif (GetV(V_MOTION,MyID) == MOTION_STAND) then
		MoveToOwner (MyID)
		TraceAI ("FOLLOW_ST -> FOLLOW_ST")
		return
	end

end



function	OnCHASE_ST ()

	TraceAI ("OnCHASE_ST")

	if (true == IsOutOfSight(MyID,MyEnemy)) then	-- ENEMY_OUTSIGHT_IN
		MyState = IDLE_ST
		MyEnemy = 0
		MyDestX, MyDestY = 0,0
		TraceAI ("CHASE_ST -> IDLE_ST : ENEMY_OUTSIGHT_IN")
		return
	end
	if (true == IsInAttackSight(MyID,MyEnemy)) then  -- ENEMY_INATTACKSIGHT_IN
		MyState = ATTACK_ST
		TraceAI ("CHASE_ST -> ATTACK_ST : ENEMY_INATTACKSIGHT_IN")
		return
	end

	local x, y = GetV (V_POSITION_APPLY_SKILLATTACKRANGE, MyEnemy, MySkill, MySkillLevel)
	if (MyDestX ~= x or MyDestY ~= y) then			-- DESTCHANGED_IN
		MyDestX, MyDestY = GetV (V_POSITION_APPLY_SKILLATTACKRANGE, MyEnemy, MySkill, MySkillLevel)
		Move (MyID,MyDestX,MyDestY)
		TraceAI ("CHASE_ST -> CHASE_ST : DESTCHANGED_IN")
		return
	end

end



function	OnATTACK_ST ()

	TraceAI ("OnATTACK_ST")
	
	if (true == IsOutOfSight(MyID,MyEnemy)) then	-- ENEMY_OUTSIGHT_IN
		MyState = IDLE_ST
		TraceAI ("ATTACK_ST -> IDLE_ST")
		return 
	end

	if (MOTION_DEAD == GetV(V_MOTION,MyEnemy)) then   -- ENEMY_DEAD_IN
		MyState = IDLE_ST
		TraceAI ("ATTACK_ST -> IDLE_ST")
		return
	end
		
	if (false == IsInAttackSight(MyID,MyEnemy)) then  -- ENEMY_OUTATTACKSIGHT_IN
		MyState = CHASE_ST
		MyDestX, MyDestY = GetV(V_POSITION_APPLY_SKILLATTACKRANGE, MyEnemy, MySkill, MySkillLevel)
		Move (MyID,MyDestX,MyDestY)
		TraceAI ("ATTACK_ST -> CHASE_ST  : ENEMY_OUTATTACKSIGHT_IN")
		return
	end
	
	if (MySkill == 0) then
		Attack (MyID,MyEnemy)
	else
		if (1 == SkillObject(MyID,MySkillLevel,MySkill,MyEnemy)) then
			MyEnemy = 0
		end
		
		MySkill = 0
	end
	TraceAI ("ATTACK_ST -> ATTACK_ST  : ENERGY_RECHARGED_IN")
	return


end



function	OnMOVE_CMD_ST ()

	TraceAI ("OnMOVE_CMD_ST")

	local x, y = GetV (V_POSITION,MyID)
	if (x == MyDestX and y == MyDestY) then				-- DESTINATION_ARRIVED_IN
		MyState = IDLE_ST
	end
end



function OnSTOP_CMD_ST ()


end



function OnATTACK_OBJECT_CMD_ST ()

	
end



function OnATTACK_AREA_CMD_ST ()

	TraceAI ("OnATTACK_AREA_CMD_ST")

	local	object = GetOwnerEnemy (MyID)
	if (object == 0) then							
		object = GetMyEnemy (MyID) 
	end

	if (object ~= 0) then							-- MYOWNER_ATTACKED_IN or ATTACKED_IN
		MyState = CHASE_ST
		MyEnemy = object
		return
	end

	local x , y = GetV (V_POSITION,MyID)
	if (x == MyDestX and y == MyDestY) then			-- DESTARRIVED_IN
			MyState = IDLE_ST
	end

end



function OnPATROL_CMD_ST ()

	TraceAI ("OnPATROL_CMD_ST")

	local	object = GetOwnerEnemy (MyID)
	if (object == 0) then							
		object = GetMyEnemy (MyID) 
	end

	if (object ~= 0) then							-- MYOWNER_ATTACKED_IN or ATTACKED_IN
		MyState = CHASE_ST
		MyEnemy = object
		TraceAI ("PATROL_CMD_ST -> CHASE_ST : ATTACKED_IN")
		return
	end

	local x , y = GetV (V_POSITION,MyID)
	if (x == MyDestX and y == MyDestY) then			-- DESTARRIVED_IN
		MyDestX = MyPatrolX
		MyDestY = MyPatrolY
		MyPatrolX = x
		MyPatrolY = y
		Move (MyID,MyDestX,MyDestY)
	end

end



function OnHOLD_CMD_ST ()

	TraceAI ("OnHOLD_CMD_ST")
	
	if (MyEnemy ~= 0) then
		local d = GetDistance(MyEnemy,MyID)
		if (d ~= -1 and d <= GetV(V_ATTACKRANGE,MyID)) then
				Attack (MyID,MyEnemy)
		else
			MyEnemy = 0
		end
		return
	end


	local	object = GetOwnerEnemy (MyID)
	if (object == 0) then							
		object = GetMyEnemy (MyID)
		if (object == 0) then						
			return
		end
	end

	MyEnemy = object

end



function OnSKILL_OBJECT_CMD_ST ()
	
end




function OnSKILL_AREA_CMD_ST ()

	TraceAI ("OnSKILL_AREA_CMD_ST")

	local x , y = GetV (V_POSITION,MyID)
	if (GetDistance(x,y,MyDestX,MyDestY) <= GetV(V_SKILLATTACKRANGE_LEVEL, MyID, MySkill, MySkillLevel)) then	-- DESTARRIVED_IN
		SkillGround (MyID,MySkillLevel,MySkill,MyDestX,MyDestY)
		MyState = IDLE_ST
		MySkill = 0
	end

end



function OnFOLLOW_CMD_ST ()

	TraceAI ("OnFOLLOW_CMD_ST")

	local ownerX, ownerY, myX, myY
	ownerX, ownerY = GetV (V_POSITION,GetV(V_OWNER,MyID)) -- 주인
	myX, myY = GetV (V_POSITION,MyID)					  -- 나 
	
	local d = GetDistance (ownerX,ownerY,myX,myY)

	if ( d <= 3) then									  -- 3셀 이하 거리면 
		return 
	end

	local motion = GetV (V_MOTION,MyID)
	if (motion == MOTION_MOVE) then                       -- 이동중
		d = GetDistance (ownerX, ownerY, MyDestX, MyDestY)
		if ( d > 3) then                                  -- 목적지 변경 ?
			MoveToOwner (MyID)
			MyDestX = ownerX
			MyDestY = ownerY
			return
		end
	else                                                  -- 다른 동작 
		MoveToOwner (MyID)
		MyDestX = ownerX
		MyDestY = ownerY
		return
	end
	
end



function	GetOwnerEnemy (myid)
	local result = 0
	local owner  = GetV (V_OWNER,myid)
	local actors = GetActors ()
	local enemys = {}
	local index = 1
	local target
	for i,v in ipairs(actors) do
		if (v ~= owner and v ~= myid) then
			target = GetV (V_TARGET,v)
			if (target == owner) then
				if (IsMonster(v) == 1) then
					enemys[index] = v
					index = index+1
				else
					local motion = GetV(V_MOTION,i)
					if (motion == MOTION_ATTACK or motion == MOTION_ATTACK2) then
						enemys[index] = v
						index = index+1
					end
				end
			end
		end
	end

	local min_dis = 100
	local dis
	for i,v in ipairs(enemys) do
		dis = GetDistance2 (myid,v)
		if (dis < min_dis) then
			result = v
			min_dis = dis
		end
	end
	
	return result
end



function	GetMyEnemy (myid)
	local result = 0

	local type = GetV (V_HOMUNTYPE,myid)
	if (type == LIF or type == LIF_H or type == AMISTR or type == AMISTR_H or type == LIF2 or type == LIF_H2 or type == AMISTR2 or type == AMISTR_H2) then
		result = GetMyEnemyA (myid)
	elseif (type == FILIR or type == FILIR_H or type == VANILMIRTH or type == VANILMIRTH_H or type == FILIR2 or type == FILIR_H2 or type == VANILMIRTH2 or type == VANILMIRTH_H2) then
		result = GetMyEnemyB (myid)
	end
	return result
end




-------------------------------------------
--  비선공형 GetMyEnemy
-------------------------------------------
function	GetMyEnemyA (myid)
	local result = 0
	local owner  = GetV (V_OWNER,myid)
	local actors = GetActors ()
	local enemys = {}
	local index = 1
	local target
	for i,v in ipairs(actors) do
		if (v ~= owner and v ~= myid) then
			target = GetV (V_TARGET,v)
			if (target == myid) then
				enemys[index] = v
				index = index+1
			end
		end
	end

	local min_dis = 100
	local dis
	for i,v in ipairs(enemys) do
		dis = GetDistance2 (myid,v)
		if (dis < min_dis) then
			result = v
			min_dis = dis
		end
	end

	return result
end





-------------------------------------------
--  선공형 GetMyEnemy
-------------------------------------------
function	GetMyEnemyB (myid)
	local result = 0
	local owner  = GetV (V_OWNER,myid)
	local actors = GetActors ()
	local enemys = {}
	local index = 1
	local type
	for i,v in ipairs(actors) do
		if (v ~= owner and v ~= myid) then
			if (1 == IsMonster(v))	then
				enemys[index] = v
				index = index+1
			end
		end
	end

	local min_dis = 100
	local dis
	for i,v in ipairs(enemys) do
		dis = GetDistance2 (myid,v)
		if (dis < min_dis) then
			result = v
			min_dis = dis
		end
	end

	return result
end



function AI(myid)

	MyID = myid
	local msg	= GetMsg (myid)			-- command
	local rmsg	= GetResMsg (myid)		-- reserved command

	
	if msg[1] == NONE_CMD then
		if rmsg[1] ~= NONE_CMD then
			if List.size(ResCmdList) < 10 then
				List.pushright (ResCmdList,rmsg) -- 예약 명령 저장
			end
		end
	else
		List.clear (ResCmdList)	-- 새로운 명령이 입력되면 예약 명령들은 삭제한다.  
		ProcessCommand (msg)	-- 명령어 처리 
	end

		
	-- 상태 처리 
 	if (MyState == IDLE_ST) then
		OnIDLE_ST ()
	elseif (MyState == CHASE_ST) then					
		OnCHASE_ST ()
	elseif (MyState == ATTACK_ST) then
		OnATTACK_ST ()
	elseif (MyState == FOLLOW_ST) then
		OnFOLLOW_ST ()
	elseif (MyState == MOVE_CMD_ST) then
		OnMOVE_CMD_ST ()
	elseif (MyState == STOP_CMD_ST) then
		OnSTOP_CMD_ST ()
	elseif (MyState == ATTACK_OBJECT_CMD_ST) then
		OnATTACK_OBJECT_CMD_ST ()
	elseif (MyState == ATTACK_AREA_CMD_ST) then
		OnATTACK_AREA_CMD_ST ()
	elseif (MyState == PATROL_CMD_ST) then
		OnPATROL_CMD_ST ()
	elseif (MyState == HOLD_CMD_ST) then
		OnHOLD_CMD_ST ()
	elseif (MyState == SKILL_OBJECT_CMD_ST) then
		OnSKILL_OBJECT_CMD_ST ()
	elseif (MyState == SKILL_AREA_CMD_ST) then
		OnSKILL_AREA_CMD_ST ()
	elseif (MyState == FOLLOW_CMD_ST) then
		OnFOLLOW_CMD_ST ()
	end

end
