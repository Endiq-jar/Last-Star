extends Node3D

const HeroScript = preload("res://scripts/laststar/hero.gd")
const WardenScript = preload("res://scripts/laststar/warden.gd")
const TouchHudScript = preload("res://scripts/laststar/touch_hud.gd")
const ShardScript = preload("res://scripts/laststar/shard.gd")

const TERRAIN_CHUNK_SIZE := 32.0
const TERRAIN_CHUNK_STEPS := 20
const TERRAIN_RENDER_DISTANCE := 3
const TREES_PER_CHUNK := 12
const ROCKS_PER_CHUNK := 5
const GRASS_PER_CHUNK := 46
const FLOWERS_PER_CHUNK := 18

var height_noise := FastNoiseLite.new()
var detail_noise := FastNoiseLite.new()
var player: CharacterBody3D
var enemies: Array[CharacterBody3D] = []
var shard_areas: Array[Area3D] = []
var shards_collected := 0
var collected_shards := PackedByteArray([0, 0, 0, 0, 0, 0])
var gold := 0
var warden_defeated := false
var warden: CharacterBody3D
var sun: DirectionalLight3D
var elapsed := 0.0
var status_timer := 0.0
var world_environment: WorldEnvironment
var terrain_material: StandardMaterial3D
var tree_trunk_mesh: CylinderMesh
var tree_leaf_mesh: CylinderMesh
var rock_mesh: SphereMesh
var grass_blade_mesh: CylinderMesh
var flower_stem_mesh: CylinderMesh
var flower_head_mesh: SphereMesh
var tree_trunk_material: StandardMaterial3D
var tree_leaf_material: StandardMaterial3D
var rock_material: StandardMaterial3D
var grass_material: StandardMaterial3D
var flower_stem_material: StandardMaterial3D
var flower_pink_material: StandardMaterial3D
var flower_gold_material: StandardMaterial3D
var terrain_chunks: Dictionary = {}
var terrain_center := Vector2i(2147483647, 2147483647)
var hud


func _ready() -> void:
	_setup_noise()
	_build_sky_and_light()
	_build_forest()
	_build_terrain()
	_build_water()
	_build_ridge_town()
	_spawn_player()
	_load_save()
	_spawn_shards()
	_spawn_warden()
	_build_hud()
	_update_objective()


func _process(delta: float) -> void:
	elapsed += delta
	if is_instance_valid(sun):
		var day_phase := fposmod(elapsed, 360.0) / 360.0
		var night := 0.5 - 0.5 * cos(day_phase * TAU)
		sun.rotation_degrees.y = 38.0 + sin(elapsed * 0.012) * 14.0
		sun.rotation_degrees.x = lerpf(-48.0, 8.0, night)
		sun.light_energy = lerpf(1.25, 0.08, night)
		sun.light_color = Color(1.0, 0.88, 0.68).lerp(Color(0.40, 0.48, 0.78), night)
		world_environment.environment.ambient_light_color = Color(0.72, 0.79, 0.94).lerp(
			Color(0.17, 0.22, 0.42), night
		)
	if status_timer > 0.0:
		status_timer -= delta
	elif is_instance_valid(hud):
		hud.set_world_status(
			"DAY %02d   •   %.0f FPS" % [1 + int(elapsed / 360.0), Engine.get_frames_per_second()]
		)
	if is_instance_valid(hud) and is_instance_valid(player):
		hud.set_vitals(player.health, player.energy, gold)
	if is_instance_valid(player):
		_update_terrain_stream()


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
	var base := Color("#559747").lerp(Color("#8cbd5b"), clampf(variation * 0.72 + 0.5, 0.0, 1.0))
	var patch := height_noise.get_noise_2d(x * 0.45 + 90.0, z * 0.45 - 42.0)
	if patch > 0.12:
		base = base.lerp(Color("#347b53"), clampf((patch - 0.12) * 0.7, 0.0, 0.48))
	var slope_x := height_at(x - 1.0, z) - height_at(x + 1.0, z)
	var slope_z := height_at(x, z - 1.0) - height_at(x, z + 1.0)
	var steepness := clampf(Vector2(slope_x, slope_z).length() * 0.18, 0.0, 0.55)
	base = base.lerp(Color("#8a8977"), steepness)
	if y > 5.5:
		var snow := clampf((y - 5.5) / 5.0, 0.0, 0.8)
		base = base.lerp(Color("#e8e8dc"), snow)
	var path_edge := clampf((3.3 - path_distance) / 1.1, 0.0, 1.0)
	path_edge = path_edge * path_edge * (3.0 - 2.0 * path_edge)
	var path_color := Color("#aa8052").lerp(
		Color("#c19b66"), clampf(variation * 0.55 + 0.5, 0.0, 1.0)
	)
	return base.lerp(path_color, path_edge)


func _build_terrain() -> void:
	terrain_material = StandardMaterial3D.new()
	terrain_material.vertex_color_use_as_albedo = true
	terrain_material.roughness = 0.96
	terrain_material.cull_mode = BaseMaterial3D.CULL_DISABLED
	_update_terrain_chunks(Vector2i.ZERO)


func _update_terrain_stream() -> void:
	var center := Vector2i(
		floori(player.position.x / TERRAIN_CHUNK_SIZE),
		floori(player.position.z / TERRAIN_CHUNK_SIZE)
	)
	if center != terrain_center:
		_update_terrain_chunks(center)


func _update_terrain_chunks(center: Vector2i) -> void:
	terrain_center = center
	var wanted: Dictionary = {}
	for z in range(center.y - TERRAIN_RENDER_DISTANCE, center.y + TERRAIN_RENDER_DISTANCE + 1):
		for x in range(center.x - TERRAIN_RENDER_DISTANCE, center.x + TERRAIN_RENDER_DISTANCE + 1):
			var coord := Vector2i(x, z)
			wanted[coord] = true
			if not terrain_chunks.has(coord):
				var chunk := _build_terrain_chunk(coord)
				terrain_chunks[coord] = chunk
				add_child(chunk)
	for old_coord in terrain_chunks.keys():
		if not wanted.has(old_coord):
			var old_chunk: Node = terrain_chunks[old_coord]
			old_chunk.queue_free()
			terrain_chunks.erase(old_coord)


func _build_terrain_chunk(coord: Vector2i) -> Node3D:
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var colors := PackedColorArray()
	var indices := PackedInt32Array()
	var step := TERRAIN_CHUNK_SIZE / float(TERRAIN_CHUNK_STEPS)
	var world_origin := Vector2(coord) * TERRAIN_CHUNK_SIZE
	for z_index in range(TERRAIN_CHUNK_STEPS + 1):
		var local_z := float(z_index) * step
		var world_z := world_origin.y + local_z
		for x_index in range(TERRAIN_CHUNK_STEPS + 1):
			var local_x := float(x_index) * step
			var world_x := world_origin.x + local_x
			var y := height_at(world_x, world_z)
			vertices.append(Vector3(local_x, y, local_z))
			var dx := height_at(world_x - 0.25, world_z) - height_at(world_x + 0.25, world_z)
			var dz := height_at(world_x, world_z - 0.25) - height_at(world_x, world_z + 0.25)
			normals.append(Vector3(dx, 0.5, dz).normalized())
			colors.append(_terrain_color(world_x, world_z, y))
	for z_index in range(TERRAIN_CHUNK_STEPS):
		for x_index in range(TERRAIN_CHUNK_STEPS):
			var a := z_index * (TERRAIN_CHUNK_STEPS + 1) + x_index
			var b := a + 1
			var c := a + TERRAIN_CHUNK_STEPS + 1
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
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	mesh.surface_set_material(0, terrain_material)
	var root := Node3D.new()
	root.name = "TerrainChunk_%d_%d" % [coord.x, coord.y]
	root.position = Vector3(world_origin.x, 0.0, world_origin.y)
	var ground := MeshInstance3D.new()
	ground.name = "Surface"
	ground.mesh = mesh
	root.add_child(ground)
	var body := StaticBody3D.new()
	body.name = "Collision"
	var collider := CollisionShape3D.new()
	collider.shape = mesh.create_trimesh_shape()
	body.add_child(collider)
	root.add_child(body)
	_decorate_terrain_chunk(root, coord)
	return root


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
	tree_trunk_mesh = CylinderMesh.new()
	tree_trunk_mesh.top_radius = 0.18
	tree_trunk_mesh.bottom_radius = 0.32
	tree_trunk_mesh.height = 2.8
	tree_trunk_material = StandardMaterial3D.new()
	tree_trunk_material.albedo_color = Color("#76513a")
	tree_trunk_material.roughness = 1.0
	tree_leaf_mesh = CylinderMesh.new()
	tree_leaf_mesh.top_radius = 0.0
	tree_leaf_mesh.bottom_radius = 1.45
	tree_leaf_mesh.height = 2.5
	tree_leaf_material = StandardMaterial3D.new()
	tree_leaf_material.albedo_color = Color("#28744b")
	tree_leaf_material.roughness = 0.9
	rock_mesh = SphereMesh.new()
	rock_mesh.radius = 0.8
	rock_mesh.height = 1.15
	rock_material = StandardMaterial3D.new()
	rock_material.albedo_color = Color("#71808b")
	rock_material.roughness = 0.95
	grass_blade_mesh = CylinderMesh.new()
	grass_blade_mesh.top_radius = 0.012
	grass_blade_mesh.bottom_radius = 0.055
	grass_blade_mesh.height = 0.52
	grass_material = StandardMaterial3D.new()
	grass_material.albedo_color = Color("#86bd59")
	grass_material.roughness = 1.0
	flower_stem_mesh = CylinderMesh.new()
	flower_stem_mesh.top_radius = 0.012
	flower_stem_mesh.bottom_radius = 0.022
	flower_stem_mesh.height = 0.38
	flower_stem_material = StandardMaterial3D.new()
	flower_stem_material.albedo_color = Color("#4b8a45")
	flower_stem_material.roughness = 1.0
	flower_head_mesh = SphereMesh.new()
	flower_head_mesh.radius = 0.085
	flower_head_mesh.height = 0.12
	flower_pink_material = StandardMaterial3D.new()
	flower_pink_material.albedo_color = Color("#f3a4c8")
	flower_pink_material.roughness = 0.72
	flower_gold_material = StandardMaterial3D.new()
	flower_gold_material.albedo_color = Color("#f4d16e")
	flower_gold_material.roughness = 0.72


func _decorate_terrain_chunk(root: Node3D, coord: Vector2i) -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = int((coord.x * 73856093) ^ (coord.y * 19349663) ^ 271828)
	var trunk_mm := _new_multimesh(tree_trunk_mesh)
	var lower_mm := _new_multimesh(tree_leaf_mesh)
	var middle_mm := _new_multimesh(tree_leaf_mesh)
	var upper_mm := _new_multimesh(tree_leaf_mesh)
	var trunk_transforms: Array[Transform3D] = []
	var lower_transforms: Array[Transform3D] = []
	var middle_transforms: Array[Transform3D] = []
	var upper_transforms: Array[Transform3D] = []
	var tree_attempts := 0
	while trunk_transforms.size() < TREES_PER_CHUNK and tree_attempts < TREES_PER_CHUNK * 8:
		tree_attempts += 1
		var local_x := rng.randf_range(1.0, TERRAIN_CHUNK_SIZE - 1.0)
		var local_z := rng.randf_range(1.0, TERRAIN_CHUNK_SIZE - 1.0)
		var world_x := coord.x * TERRAIN_CHUNK_SIZE + local_x
		var world_z := coord.y * TERRAIN_CHUNK_SIZE + local_z
		if absf(world_z - path_center(world_x)) < 5.5:
			continue
		if Vector2(world_x, world_z).length() < 13.0:
			continue
		if absf(world_x) < 34.0 and world_z < -54.0:
			continue
		var ground_y := height_at(world_x, world_z)
		if ground_y > 8.0:
			continue
		var scale := rng.randf_range(0.72, 1.42)
		var basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(Vector3.ONE * scale)
		var origin := Vector3(local_x, ground_y, local_z)
		trunk_transforms.append(Transform3D(basis, origin + Vector3.UP * 1.4 * scale))
		lower_transforms.append(Transform3D(basis, origin + Vector3.UP * 2.4 * scale))
		middle_transforms.append(Transform3D(basis, origin + Vector3.UP * 3.4 * scale))
		upper_transforms.append(Transform3D(basis, origin + Vector3.UP * 4.3 * scale))
	_fill_multimesh(trunk_mm, trunk_transforms)
	_fill_multimesh(lower_mm, lower_transforms)
	_fill_multimesh(middle_mm, middle_transforms)
	_fill_multimesh(upper_mm, upper_transforms)
	_add_multimesh(root, "TreeTrunks", trunk_mm, tree_trunk_material)
	_add_multimesh(root, "CanopyLower", lower_mm, tree_leaf_material)
	_add_multimesh(root, "CanopyMiddle", middle_mm, tree_leaf_material)
	_add_multimesh(root, "CanopyUpper", upper_mm, tree_leaf_material)
	var rock_mm := _new_multimesh(rock_mesh)
	var rock_transforms: Array[Transform3D] = []
	for i in range(ROCKS_PER_CHUNK):
		var local_x := rng.randf_range(1.0, TERRAIN_CHUNK_SIZE - 1.0)
		var local_z := rng.randf_range(1.0, TERRAIN_CHUNK_SIZE - 1.0)
		var world_x := coord.x * TERRAIN_CHUNK_SIZE + local_x
		var world_z := coord.y * TERRAIN_CHUNK_SIZE + local_z
		if absf(world_z - path_center(world_x)) < 3.5:
			continue
		if absf(world_x + 38.0 + (world_z + 26.0) * 0.12) < 4.0:
			continue
		var scale := rng.randf_range(0.35, 1.15)
		var point := Vector3(local_x, height_at(world_x, world_z) + 0.1, local_z)
		var rotation := Basis(Vector3.UP, rng.randf_range(0.0, TAU))
		rock_transforms.append(
			Transform3D(rotation.scaled(Vector3(scale, scale * 0.65, scale)), point)
		)
	_fill_multimesh(rock_mm, rock_transforms)
	_add_multimesh(root, "MeadowRocks", rock_mm, rock_material)
	_add_meadow_details(root, coord, rng)


func _add_meadow_details(root: Node3D, coord: Vector2i, rng: RandomNumberGenerator) -> void:
	var grass_mm := _new_multimesh(grass_blade_mesh)
	var stems_mm := _new_multimesh(flower_stem_mesh)
	var pink_mm := _new_multimesh(flower_head_mesh)
	var gold_mm := _new_multimesh(flower_head_mesh)
	var grass_transforms: Array[Transform3D] = []
	var stem_transforms: Array[Transform3D] = []
	var pink_transforms: Array[Transform3D] = []
	var gold_transforms: Array[Transform3D] = []
	var grass_attempts := 0
	while grass_transforms.size() < GRASS_PER_CHUNK and grass_attempts < GRASS_PER_CHUNK * 3:
		grass_attempts += 1
		var local_x := rng.randf_range(0.0, TERRAIN_CHUNK_SIZE)
		var local_z := rng.randf_range(0.0, TERRAIN_CHUNK_SIZE)
		var world_x := coord.x * TERRAIN_CHUNK_SIZE + local_x
		var world_z := coord.y * TERRAIN_CHUNK_SIZE + local_z
		if absf(world_z - path_center(world_x)) < 4.0:
			continue
		if absf(world_x + 38.0 + (world_z + 26.0) * 0.12) < 3.5:
			continue
		if height_at(world_x, world_z) > 5.8:
			continue
		var blade_height := rng.randf_range(0.58, 1.18)
		var blade_basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(
			Vector3(rng.randf_range(0.7, 1.25), blade_height, rng.randf_range(0.7, 1.25))
		)
		grass_transforms.append(
			Transform3D(
				blade_basis,
				Vector3(local_x, height_at(world_x, world_z) + blade_height * 0.25, local_z)
			)
		)
	var flower_attempts := 0
	while (
		pink_transforms.size() + gold_transforms.size() < FLOWERS_PER_CHUNK
		and flower_attempts < FLOWERS_PER_CHUNK * 5
	):
		flower_attempts += 1
		var local_x := rng.randf_range(0.0, TERRAIN_CHUNK_SIZE)
		var local_z := rng.randf_range(0.0, TERRAIN_CHUNK_SIZE)
		var world_x := coord.x * TERRAIN_CHUNK_SIZE + local_x
		var world_z := coord.y * TERRAIN_CHUNK_SIZE + local_z
		if absf(world_z - path_center(world_x)) < 3.8:
			continue
		if absf(world_x + 38.0 + (world_z + 26.0) * 0.12) < 3.5:
			continue
		if height_at(world_x, world_z) > 5.5:
			continue
		var ground_y := height_at(world_x, world_z)
		var stem_basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU)).scaled(
			Vector3.ONE * rng.randf_range(0.8, 1.2)
		)
		stem_transforms.append(Transform3D(stem_basis, Vector3(local_x, ground_y + 0.19, local_z)))
		var head_origin := Vector3(local_x, ground_y + rng.randf_range(0.39, 0.48), local_z)
		var head_transform := Transform3D(
			Basis.IDENTITY.scaled(Vector3.ONE * rng.randf_range(0.8, 1.2)), head_origin
		)
		if rng.randf() < 0.55:
			pink_transforms.append(head_transform)
		else:
			gold_transforms.append(head_transform)
	_fill_multimesh(grass_mm, grass_transforms)
	_fill_multimesh(stems_mm, stem_transforms)
	_fill_multimesh(pink_mm, pink_transforms)
	_fill_multimesh(gold_mm, gold_transforms)
	_add_multimesh(root, "MeadowGrass", grass_mm, grass_material)
	_add_multimesh(root, "WildflowerStems", stems_mm, flower_stem_material)
	_add_multimesh(root, "PinkWildflowers", pink_mm, flower_pink_material)
	_add_multimesh(root, "GoldenWildflowers", gold_mm, flower_gold_material)


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


func _add_multimesh(parent: Node3D, node_name: String, data: MultiMesh, material: Material) -> void:
	var mesh_instance := MultiMeshInstance3D.new()
	mesh_instance.name = node_name
	mesh_instance.multimesh = data
	mesh_instance.material_override = material
	parent.add_child(mesh_instance)


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


func _spawn_shards() -> void:
	var positions := [
		Vector2(-19.0, -15.0),
		Vector2(18.0, -11.0),
		Vector2(-34.0, -39.0),
		Vector2(31.0, -42.0),
		Vector2(3.0, -59.0),
		Vector2(-52.0, 12.0)
	]
	var crystal_mesh := SphereMesh.new()
	crystal_mesh.radius = 0.34
	crystal_mesh.height = 0.9
	var crystal_material := StandardMaterial3D.new()
	crystal_material.albedo_color = Color("#f7b769")
	crystal_material.emission_enabled = true
	crystal_material.emission = Color("#dc9846")
	crystal_material.roughness = 0.25
	for i in range(positions.size()):
		if collected_shards[i] != 0:
			continue
		var point: Vector2 = positions[i]
		var area := Area3D.new()
		area.name = "StarlightShard_%d" % i
		area.set_script(ShardScript)
		area.set("shard_index", i)
		area.set("float_phase", float(i) * 0.9)
		area.set("base_y", height_at(point.x, point.y) + 1.2)
		area.position = Vector3(point.x, height_at(point.x, point.y) + 1.2, point.y)
		var trigger := CollisionShape3D.new()
		var radius := SphereShape3D.new()
		radius.radius = 1.45
		trigger.shape = radius
		area.add_child(trigger)
		var visual := MeshInstance3D.new()
		visual.mesh = crystal_mesh
		visual.material_override = crystal_material
		area.add_child(visual)
		area.body_entered.connect(_on_shard_body_entered.bind(area, i))
		add_child(area)
		shard_areas.append(area)


func _on_shard_body_entered(body: Node3D, area: Area3D, shard_index: int) -> void:
	if body != player or not is_instance_valid(area):
		return
	if (
		shard_index < 0
		or shard_index >= collected_shards.size()
		or collected_shards[shard_index] != 0
	):
		return
	collected_shards[shard_index] = 1
	area.set_deferred("monitoring", false)
	area.queue_free()
	shard_areas.erase(area)
	shards_collected = mini(6, shards_collected + 1)
	gold += 25
	player.energy = mini(100, player.energy + 20)
	_set_status("STAR SHARD FOUND  •  +25 GOLD")
	if shards_collected >= 6 and is_instance_valid(warden):
		warden.call("set_active", true)
		_set_status("THE WARDEN AWAKENS")
	_update_objective()


func _update_objective() -> void:
	if not is_instance_valid(hud):
		return
	if shards_collected < 6:
		hud.set_objective("QUEST 01  /  Find the six starlight shards  •  %d/6" % shards_collected)
	elif not warden_defeated:
		hud.set_objective("QUEST 02  /  Defeat the awakened Warden")
	else:
		hud.set_objective("VALLEY RESTORED  /  Explore the ridge town")


func _spawn_warden() -> void:
	if warden_defeated:
		return
	var enemy := CharacterBody3D.new()
	enemy.name = "Warden"
	enemy.set_script(WardenScript)
	enemy.position = Vector3(7.0, height_at(7.0, -18.0) + 1.0, -18.0)
	add_child(enemy)
	enemy.set("target", player)
	enemy.call("set_active", shards_collected >= 6)
	warden = enemy
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
	if shards_collected < 6:
		_set_status("FIND ALL SIX SHARDS FIRST")
		return
	for enemy in enemies:
		if (
			is_instance_valid(enemy)
			and enemy.global_position.distance_to(player.global_position) <= radius
		):
			enemy.call("take_damage", amount)
			if int(enemy.get("health")) <= 0:
				enemies.erase(enemy)
				enemy.queue_free()
				warden_defeated = true
				gold += 100
				_set_status("WARDEN DEFEATED  •  +100 GOLD")
				_update_objective()
			return
	_set_status("NO TARGET IN RANGE")


func _set_status(text: String) -> void:
	status_timer = 2.4
	if is_instance_valid(hud):
		hud.set_world_status(text)


func _load_save() -> void:
	var path := "user://last_star_godot.json"
	if not FileAccess.file_exists(path):
		return
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null:
		return
	var decoded: Variant = JSON.parse_string(file.get_as_text())
	file.close()
	if not decoded is Dictionary:
		return
	var payload: Dictionary = decoded.duplicate(true)
	var checksum := str(payload.get("checksum", ""))
	payload.erase("checksum")
	if int(payload.get("schema", 0)) != 1 or checksum != _sha256(JSON.stringify(payload, "", true)):
		return
	var world_data: Dictionary = payload.get("world", {})
	var player_data: Dictionary = payload.get("player", {})
	var progress: Dictionary = payload.get("progress", {})
	elapsed = clampf(float(world_data.get("time", 0.0)), 0.0, 100000000.0)
	var saved_position: Variant = player_data.get("position", [])
	if saved_position is Array and saved_position.size() == 3:
		var saved_x := float(saved_position[0])
		var saved_z := float(saved_position[2])
		if maxf(absf(saved_x), absf(saved_z)) < 100000.0:
			player.position = Vector3(saved_x, height_at(saved_x, saved_z) + 1.2, saved_z)
			_update_terrain_chunks(
				Vector2i(floori(saved_x / TERRAIN_CHUNK_SIZE), floori(saved_z / TERRAIN_CHUNK_SIZE))
			)
	player.health = clampi(int(player_data.get("health", 100)), 1, 100)
	player.energy = clampi(int(player_data.get("energy", 100)), 0, 100)
	gold = clampi(int(progress.get("gold", 0)), 0, 1000000)
	warden_defeated = bool(progress.get("warden_defeated", false))
	var shard_data: Variant = progress.get("collected_shards", [])
	if shard_data is Array and shard_data.size() == collected_shards.size():
		shards_collected = 0
		for i in range(collected_shards.size()):
			collected_shards[i] = 1 if bool(shard_data[i]) else 0
			shards_collected += collected_shards[i]


func _save_game() -> void:
	if not is_instance_valid(player):
		return
	var saved_shards: Array[bool] = []
	for value in collected_shards:
		saved_shards.append(value != 0)
	var payload := {
		"schema": 1,
		"world": {"time": elapsed},
		"player":
		{
			"position": [player.position.x, player.position.y, player.position.z],
			"health": player.health,
			"energy": player.energy
		},
		"progress":
		{"gold": gold, "collected_shards": saved_shards, "warden_defeated": warden_defeated}
	}
	var canonical := JSON.stringify(payload, "", true)
	payload["checksum"] = _sha256(canonical)
	var temporary := "user://last_star_godot.json.tmp"
	var output := FileAccess.open(temporary, FileAccess.WRITE)
	if output == null:
		return
	output.store_string(JSON.stringify(payload, "", true))
	output.flush()
	output.close()
	var source_path := ProjectSettings.globalize_path(temporary)
	var target_path := ProjectSettings.globalize_path("user://last_star_godot.json")
	if DirAccess.rename_absolute(source_path, target_path) != OK:
		DirAccess.remove_absolute(source_path)


func _sha256(text: String) -> String:
	var context := HashingContext.new()
	if context.start(HashingContext.HASH_SHA256) != OK:
		return ""
	context.update(text.to_utf8_buffer())
	return context.finish().hex_encode()


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_APPLICATION_PAUSED:
		_save_game()
