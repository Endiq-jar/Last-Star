extends Node3D

const HeroScript = preload("res://scripts/laststar/hero.gd")
const WardenScript = preload("res://scripts/laststar/warden.gd")
const TouchHudScript = preload("res://scripts/laststar/touch_hud.gd")

const TERRAIN_SIZE := 180.0
const TERRAIN_STEPS := 120
const TREE_COUNT := 380
const ROCK_COUNT := 110

var height_noise := FastNoiseLite.new()
var detail_noise := FastNoiseLite.new()
var player: CharacterBody3D
var enemies: Array[CharacterBody3D] = []
var sun: DirectionalLight3D
var elapsed := 0.0
var status_timer := 0.0
var world_environment: WorldEnvironment
var hud


func _ready() -> void:
	_setup_noise()
	_build_sky_and_light()
	_build_terrain()
	_build_water()
	_build_forest()
	_build_ridge_town()
	_spawn_player()
	_spawn_warden()
	_build_hud()
	_load_save()


func _process(delta: float) -> void:
	elapsed += delta
	if is_instance_valid(sun):
		sun.rotation_degrees.y = 38.0 + sin(elapsed * 0.012) * 14.0
	if status_timer > 0.0:
		status_timer -= delta
	elif is_instance_valid(hud):
		hud.set_world_status(
			"DAY %02d   •   %.0f FPS" % [1 + int(elapsed / 180.0), Engine.get_frames_per_second()]
		)
	if is_instance_valid(hud) and is_instance_valid(player):
		hud.set_vitals(player.health, player.energy)


func _setup_noise() -> void:
	height_noise.seed = 271828
	height_noise.frequency = 0.014
	height_noise.fractal_octaves = 4
	height_noise.fractal_lacunarity = 2.1
	detail_noise.seed = 314159
	detail_noise.frequency = 0.12
	detail_noise.fractal_octaves = 2


func height_at(x: float, z: float) -> float:
	var broad := height_noise.get_noise_2d(x, z) * 4.0
	var detail := detail_noise.get_noise_2d(x, z) * 0.48
	var ridge_mask := clampf((absf(x) - 34.0) / 38.0, 0.0, 1.0)
	var ridge := ridge_mask * (2.5 + 2.5 * height_noise.get_noise_2d(x * 0.43 + 40.0, z * 0.38))
	var river_distance := absf(x + 38.0 + (z + 26.0) * 0.12)
	var river_blend := 1.0 - clampf((river_distance - 3.0) / 8.0, 0.0, 1.0)
	river_blend = river_blend * river_blend * (3.0 - 2.0 * river_blend)
	return lerpf(broad + detail + ridge, -0.72, river_blend)


func path_center(x: float) -> float:
	return -3.5 - 0.18 * x + 7.0 * sin(x * 0.027)


func _terrain_color(x: float, z: float, y: float) -> Color:
	var variation := detail_noise.get_noise_2d(x * 2.0, z * 2.0)
	var path_distance := absf(z - path_center(x))
	if path_distance < 2.2:
		return Color(0.49, 0.34, 0.23).lerp(
			Color(0.62, 0.45, 0.29), clampf(variation * 0.55 + 0.5, 0.0, 1.0)
		)
	if y > 4.2:
		return Color(0.48, 0.55, 0.46).lerp(
			Color(0.78, 0.81, 0.72), clampf((y - 4.2) / 8.0, 0.0, 0.65)
		)
	var base := Color(0.28, 0.58, 0.29).lerp(
		Color(0.42, 0.69, 0.34), clampf(variation * 0.65 + 0.5, 0.0, 1.0)
	)
	if absf(x) > 47.0 and y > 2.0:
		base = base.lerp(Color(0.42, 0.48, 0.36), 0.28)
	return base


func _build_terrain() -> void:
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var colors := PackedColorArray()
	var indices := PackedInt32Array()
	var step := TERRAIN_SIZE / float(TERRAIN_STEPS)
	var half := TERRAIN_SIZE * 0.5
	for z_index in range(TERRAIN_STEPS + 1):
		var z := -half + float(z_index) * step
		for x_index in range(TERRAIN_STEPS + 1):
			var x := -half + float(x_index) * step
			var y := height_at(x, z)
			vertices.append(Vector3(x, y, z))
			var dx := height_at(x - 0.25, z) - height_at(x + 0.25, z)
			var dz := height_at(x, z - 0.25) - height_at(x, z + 0.25)
			normals.append(Vector3(dx, 0.5, dz).normalized())
			colors.append(_terrain_color(x, z, y))
	for z_index in range(TERRAIN_STEPS):
		for x_index in range(TERRAIN_STEPS):
			var a := z_index * (TERRAIN_STEPS + 1) + x_index
			var b := a + 1
			var c := a + TERRAIN_STEPS + 1
			var d := c + 1
			indices.append(a)
			indices.append(c)
			indices.append(b)
			indices.append(b)
			indices.append(c)
			indices.append(d)
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_COLOR] = colors
	arrays[Mesh.ARRAY_INDEX] = indices
	var terrain_mesh := ArrayMesh.new()
	terrain_mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	var terrain_material := StandardMaterial3D.new()
	terrain_material.vertex_color_use_as_albedo = true
	terrain_material.roughness = 0.96
	terrain_material.cull_mode = BaseMaterial3D.CULL_DISABLED
	terrain_mesh.surface_set_material(0, terrain_material)
	var ground := MeshInstance3D.new()
	ground.name = "LushMeadow"
	ground.mesh = terrain_mesh
	add_child(ground)
	var collider := StaticBody3D.new()
	collider.name = "TerrainCollision"
	var collision_shape := CollisionShape3D.new()
	collision_shape.shape = terrain_mesh.create_trimesh_shape()
	collider.add_child(collision_shape)
	add_child(collider)


func _build_sky_and_light() -> void:
	world_environment = WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var sky_material := ProceduralSkyMaterial.new()
	sky_material.sky_top_color = Color("#4d82c8")
	sky_material.sky_horizon_color = Color("#f6dca2")
	sky_material.ground_bottom_color = Color("#718768")
	sky_material.ground_horizon_color = Color("#a8b58a")
	sky_material.sun_angle_max = 18.0
	sky.sky_material = sky_material
	environment.sky = sky
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color(0.72, 0.79, 0.94)
	environment.ambient_light_energy = 0.62
	environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	world_environment.environment = environment
	add_child(world_environment)
	sun = DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48.0, 38.0, 0.0)
	sun.light_color = Color(1.0, 0.88, 0.68)
	sun.light_energy = 1.25
	sun.shadow_enabled = true
	add_child(sun)


func _build_water() -> void:
	var water := MeshInstance3D.new()
	water.name = "River"
	var plane := PlaneMesh.new()
	plane.size = Vector2(30.0, 100.0)
	water.mesh = plane
	water.position = Vector3(-38.0, -0.4, -26.0)
	water.rotation_degrees.y = -12.0
	var material := StandardMaterial3D.new()
	material.albedo_color = Color(0.11, 0.52, 0.72, 0.84)
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	material.roughness = 0.18
	material.metallic = 0.08
	material.cull_mode = BaseMaterial3D.CULL_DISABLED
	water.material_override = material
	add_child(water)


func _build_forest() -> void:
	var trunk := CylinderMesh.new()
	trunk.top_radius = 0.18
	trunk.bottom_radius = 0.32
	trunk.height = 2.8
	var trunk_material := StandardMaterial3D.new()
	trunk_material.albedo_color = Color("#76513a")
	trunk_material.roughness = 1.0
	var leaf_mesh := CylinderMesh.new()
	leaf_mesh.top_radius = 0.0
	leaf_mesh.bottom_radius = 1.45
	leaf_mesh.height = 2.5
	var leaf_material := StandardMaterial3D.new()
	leaf_material.albedo_color = Color("#28744b")
	leaf_material.roughness = 0.9
	var trunk_mm := _new_multimesh(trunk)
	var leaf_low_mm := _new_multimesh(leaf_mesh)
	var leaf_mid_mm := _new_multimesh(leaf_mesh)
	var leaf_top_mm := _new_multimesh(leaf_mesh)
	var trunk_transforms: Array[Transform3D] = []
	var leaf_low_transforms: Array[Transform3D] = []
	var leaf_mid_transforms: Array[Transform3D] = []
	var leaf_top_transforms: Array[Transform3D] = []
	var rng := RandomNumberGenerator.new()
	rng.seed = 271828
	var placed := 0
	while placed < TREE_COUNT:
		var x := rng.randf_range(-84.0, 84.0)
		var z := rng.randf_range(-84.0, 84.0)
		if absf(z - path_center(x)) < 6.0 or Vector2(x, z).length() < 12.0:
			continue
		if absf(x) < 19.0 and z > -35.0:
			continue
		var y := height_at(x, z)
		if y > 8.0:
			continue
		var scale := rng.randf_range(0.72, 1.45)
		var yaw := rng.randf_range(0.0, TAU)
		var origin := Vector3(x, y, z)
		var basis := Basis(Vector3.UP, yaw).scaled(Vector3.ONE * scale)
		trunk_transforms.append(Transform3D(basis, origin + Vector3.UP * 1.4 * scale))
		leaf_low_transforms.append(Transform3D(basis, origin + Vector3.UP * 2.4 * scale))
		leaf_mid_transforms.append(Transform3D(basis, origin + Vector3.UP * 3.4 * scale))
		leaf_top_transforms.append(Transform3D(basis, origin + Vector3.UP * 4.3 * scale))
		placed += 1
	_fill_multimesh(trunk_mm, trunk_transforms)
	_fill_multimesh(leaf_low_mm, leaf_low_transforms)
	_fill_multimesh(leaf_mid_mm, leaf_mid_transforms)
	_fill_multimesh(leaf_top_mm, leaf_top_transforms)
	_add_multimesh("TreeTrunks", trunk_mm, trunk_material)
	_add_multimesh("CanopyLower", leaf_low_mm, leaf_material)
	_add_multimesh("CanopyMiddle", leaf_mid_mm, leaf_material)
	_add_multimesh("CanopyUpper", leaf_top_mm, leaf_material)
	var rock_mesh := SphereMesh.new()
	rock_mesh.radius = 0.8
	rock_mesh.height = 1.15
	var rock_material := StandardMaterial3D.new()
	rock_material.albedo_color = Color("#71808b")
	rock_material.roughness = 0.95
	var rock_mm := _new_multimesh(rock_mesh)
	var rock_transforms: Array[Transform3D] = []
	for i in range(ROCK_COUNT):
		var x := rng.randf_range(-80.0, 80.0)
		var z := rng.randf_range(-80.0, 80.0)
		if absf(z - path_center(x)) < 4.0:
			continue
		var scale := rng.randf_range(0.35, 1.2)
		var point := Vector3(x, height_at(x, z) + 0.1, z)
		rock_transforms.append(
			Transform3D(
				Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(
					Vector3(scale, scale * 0.65, scale)
				),
				point
			)
		)
	_fill_multimesh(rock_mm, rock_transforms)
	_add_multimesh("MeadowRocks", rock_mm, rock_material)


func _new_multimesh(mesh: Mesh) -> MultiMesh:
	var result := MultiMesh.new()
	result.transform_format = MultiMesh.TRANSFORM_3D
	result.mesh = mesh
	result.instance_count = 0
	return result


func _fill_multimesh(multimesh: MultiMesh, transforms: Array[Transform3D]) -> void:
	multimesh.instance_count = transforms.size()
	for i in range(transforms.size()):
		multimesh.set_instance_transform(i, transforms[i])


func _add_multimesh(node_name: String, data: MultiMesh, material: Material) -> void:
	var mesh_instance := MultiMeshInstance3D.new()
	mesh_instance.name = node_name
	mesh_instance.multimesh = data
	mesh_instance.material_override = material
	add_child(mesh_instance)


func _build_ridge_town() -> void:
	var wall_material := StandardMaterial3D.new()
	wall_material.albedo_color = Color("#e4d8b6")
	wall_material.roughness = 0.9
	var roof_material := StandardMaterial3D.new()
	roof_material.albedo_color = Color("#bf7357")
	roof_material.roughness = 0.9
	roof_material.cull_mode = BaseMaterial3D.CULL_DISABLED
	var tower_material := StandardMaterial3D.new()
	tower_material.albedo_color = Color("#d4c8a9")
	var rng := RandomNumberGenerator.new()
	rng.seed = 112358
	for i in range(13):
		var x := -24.0 + float(i % 7) * 8.0 + rng.randf_range(-1.2, 1.2)
		var z := -65.0 - float(i / 7) * 7.0 + rng.randf_range(-1.0, 1.0)
		var base := Vector3(x, height_at(x, z), z)
		_add_box(
			"CottageWall_%02d" % i,
			Vector3(4.7, 3.1, 4.1),
			base + Vector3(0, 1.55, 0),
			wall_material
		)
		_add_roof(base + Vector3(0, 3.3, 0), roof_material, 2.65, 2.2)
	for i in range(3):
		var x := -25.0 + i * 25.0
		var z := -75.0
		var base := Vector3(x, height_at(x, z), z)
		_add_box(
			"RidgeTower_%d" % i, Vector3(6.0, 11.0, 6.0), base + Vector3(0, 5.5, 0), tower_material
		)
		_add_roof(base + Vector3(0, 11.2, 0), roof_material, 3.5, 3.0)


func _add_box(node_name: String, box_size: Vector3, at: Vector3, material: Material) -> void:
	var instance := MeshInstance3D.new()
	instance.name = node_name
	var mesh := BoxMesh.new()
	mesh.size = box_size
	instance.mesh = mesh
	instance.material_override = material
	instance.position = at
	add_child(instance)
	var body := StaticBody3D.new()
	body.name = node_name + "Collision"
	body.position = at
	var shape := CollisionShape3D.new()
	var box_shape := BoxShape3D.new()
	box_shape.size = box_size
	shape.shape = box_shape
	body.add_child(shape)
	add_child(body)


func _add_roof(at: Vector3, material: Material, half_width: float, height: float) -> void:
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	var vertices := PackedVector3Array(
		[
			Vector3(-half_width, 0, -half_width),
			Vector3(half_width, 0, -half_width),
			Vector3(0, height, -half_width),
			Vector3(half_width, 0, half_width),
			Vector3(-half_width, 0, half_width),
			Vector3(0, height, half_width),
			Vector3(-half_width, 0, -half_width),
			Vector3(-half_width, 0, half_width),
			Vector3(0, height, 0),
			Vector3(half_width, 0, half_width),
			Vector3(half_width, 0, -half_width),
			Vector3(0, height, 0)
		]
	)
	var indices := PackedInt32Array([0, 2, 1, 3, 5, 4, 6, 8, 7, 9, 11, 10])
	var normals := PackedVector3Array()
	normals.resize(vertices.size())
	for face_start in range(0, indices.size(), 3):
		var ia := indices[face_start]
		var ib := indices[face_start + 1]
		var ic := indices[face_start + 2]
		var normal := (vertices[ib] - vertices[ia]).cross(vertices[ic] - vertices[ia]).normalized()
		normals[ia] = normal
		normals[ib] = normal
		normals[ic] = normal
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_INDEX] = indices
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	mesh.surface_set_material(0, material)
	var instance := MeshInstance3D.new()
	instance.mesh = mesh
	instance.position = at
	add_child(instance)


func _spawn_player() -> void:
	player = CharacterBody3D.new()
	player.name = "Player"
	player.set_script(HeroScript)
	player.position = Vector3(0.0, height_at(0, 0) + 1.2, 0.0)
	add_child(player)
	player.set("world_root", self)


func _spawn_warden() -> void:
	var enemy := CharacterBody3D.new()
	enemy.name = "Warden"
	enemy.set_script(WardenScript)
	enemy.position = Vector3(7.0, height_at(7.0, -18.0) + 1.0, -18.0)
	add_child(enemy)
	enemy.set("target", player)
	enemies.append(enemy)


func _build_hud() -> void:
	hud = CanvasLayer.new()
	hud.name = "MobileHUD"
	hud.set_script(TouchHudScript)
	add_child(hud)
	hud.move_changed.connect(player.set_move_input)
	hud.camera_drag.connect(player.rotate_camera)
	hud.action_pressed.connect(_on_action)


func _on_action(action_name: String) -> void:
	if action_name == "jump":
		player.request_jump()
	elif action_name == "attack":
		player.attack()
	elif action_name == "skill":
		player.cast_skill()
	elif action_name == "sprint":
		player.set_sprinting(true)
	elif action_name == "sprint_release":
		player.set_sprinting(false)


func damage_nearest_enemy(amount: int, radius: float) -> void:
	for enemy in enemies:
		if (
			is_instance_valid(enemy)
			and enemy.global_position.distance_to(player.global_position) <= radius
		):
			enemy.call("take_damage", amount)
			if int(enemy.get("health")) <= 0:
				enemies.erase(enemy)
				enemy.queue_free()
				_set_status("WARDEN DEFEATED")
			return
	_set_status("NO TARGET IN RANGE")


func _set_status(text: String) -> void:
	status_timer = 2.4
	if is_instance_valid(hud):
		hud.set_world_status(text)


func _load_save() -> void:
	var data := ConfigFile.new()
	if data.load("user://last_star_godot.cfg") == OK:
		var saved: Vector3 = data.get_value("player", "position", player.position)
		if saved.length() < TERRAIN_SIZE * 0.6:
			player.position = Vector3(saved.x, height_at(saved.x, saved.z) + 1.2, saved.z)


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_PREDELETE:
		if is_instance_valid(player):
			var data := ConfigFile.new()
			data.set_value("player", "position", Vector3(player.position.x, 0.0, player.position.z))
			data.save("user://last_star_godot.cfg")
